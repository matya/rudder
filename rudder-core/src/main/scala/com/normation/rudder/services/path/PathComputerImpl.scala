/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package com.normation.rudder.services.path

import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain._
import org.apache.commons.io.FilenameUtils
import com.normation.rudder.domain.servers._
import com.normation.rudder.repository._
import com.normation.rudder.exceptions._
import com.normation.exceptions._
import net.liftweb.common._
import com.normation.inventory.domain.AgentType
import com.normation.inventory.domain.NOVA_AGENT
import com.normation.inventory.domain.COMMUNITY_AGENT
import com.normation.exceptions.BusinessException
import com.normation.rudder.services.policies.nodeconfig.NodeConfiguration

/**
 * Utilitary tool to compute the path of a machine promises (and others information) on the rootMachine
 *
 */
class PathComputerImpl(
  backupFolder: String // /var/rudder/backup/
) extends PathComputer with Loggable {


  private[this] val promisesPrefix = "/rules"
  private[this] val newPostfix = ".new"

  private[this] val baseFolder = Constants.NODE_PROMISES_PARENT_DIR_BASE
  private[this] val relativeShareFolder = Constants.NODE_PROMISES_PARENT_DIR

  /**
   * Compute
   *  the path of promises for a node, i.e. the full path on the root server to the data
   *  the new folder, i.e. the full path where promises are written and checked before being updated
   *  the backup folder, the path were promises are backuped
   * Finish with no trailing /
   * Ex : /var/rudder/share/uuid-a/share/uuid-b/rules,/var/rudder/share/uuid-a/share/uuid-b/rules.new, /var/rudder/backup/uuid-a/share/uuid-b
   * Caution: when used for root server, the computed path is not valid, however some magic catch it
   * up after to correct the path
   * @param searchedNodeConfiguration : the machine we search
   * @return
   */
  def computeBaseNodePath(searchedNodeId : NodeId, rootNodeId: NodeId, allNodeConfigs: Map[NodeId, NodeConfiguration]): Box[((String, String, String))] = {
    for {
      path <- recurseComputePath(rootNodeId, searchedNodeId, "/"  + searchedNodeId.value, allNodeConfigs)
    } yield {
      (
          FilenameUtils.normalize(baseFolder + relativeShareFolder + "/" + path + promisesPrefix)
        , FilenameUtils.normalize(baseFolder + relativeShareFolder + "/" + path + promisesPrefix + newPostfix)
        , FilenameUtils.normalize(backupFolder + path + promisesPrefix))
    }
  }

  /**
   * Return the path of the promises for the root (we directly write its promises in its path)
   * @param agent
   * @return
   */
  def getRootPath(agentType : AgentType) : String = {
    agentType match {
        case NOVA_AGENT => Constants.CFENGINE_NOVA_PROMISES_PATH
        case COMMUNITY_AGENT => Constants.CFENGINE_COMMUNITY_PROMISES_PATH
        case x => throw new BusinessException("Unrecognized agent type: %s".format(x))
    }
  }


  /**
   * Return the path from a machine to another, excluding the top rootNode
   * If we have the hierarchy Root, A, B :
   * recurseComputePath(root, B, path) will return : machineA/share/ + path
   * recurseComputePath(A, B, path) will return : machineA/share/+ path
   * recurseComputePath(root, A, path) will return :  path
   * @param fromNode
   * @param toNode
   * @param path
   * @return
   */
  private def recurseComputePath(fromNodeId: NodeId, toNodeId: NodeId, path : String, allNodeConfig: Map[NodeId, NodeConfiguration]) : Box[String] = {
    if (fromNodeId == toNodeId) {
      Full(path)
    } else {
      for {
        toNode <- Box(allNodeConfig.get(toNodeId)) ?~! s"Missing node with id ${toNodeId.value} when trying to build the promise files path for node ${fromNodeId.value}"
        pid    =  toNode.nodeInfo.policyServerId
        parent <- Box(allNodeConfig.get(pid)) ?~! s"Can not find the parent node (${pid.value}) of node ${toNodeId.value} when trying to build the promise files for node ${fromNodeId.value}"
        result <- parent match {
                    case root if root.nodeInfo.id == NodeId("root") =>
                        // root is a specific case, it is the root of everything
                        recurseComputePath(fromNodeId, root.nodeInfo.id, path, allNodeConfig)

                    case policyParent =>
                        recurseComputePath(fromNodeId, policyParent.nodeInfo.id, policyParent.nodeInfo.id.value + "/" + relativeShareFolder + "/" + path, allNodeConfig)
                  }
      } yield {
        result
      }
    }
  }
}
