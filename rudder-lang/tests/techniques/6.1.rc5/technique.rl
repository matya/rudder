# This file has been generated with rltranslate
@format=0
@name = "normal"
@description = "ewf"
@category = "ncf_technique"
@version = 0
@parameters = [
@  { "name" = "parameter wdd", "id" = "c6e6cc3a-9ce8-4889-bccc-6bfc1b091d0d", "description" = "" },
@  { "name" = "paramtest", "id" = "d74a03dd-5b0b-4b06-8dcf-b4e0cb387c60", "description" = ""},
@]


resource normal(parameter_wdd,paramtest)
normal state technique() {
  @component = "Condition once"
  condition("mycond").once() as condition_once_mycond
}
