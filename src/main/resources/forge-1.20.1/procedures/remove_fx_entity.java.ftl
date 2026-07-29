RemoveFXEntityServer.destroy(${input$Entity})
<#if field_list$Name?size gt 0>
    .name("${field_list$Name[0]}")
</#if>
<#if input_list$Force?size gt 0>
    .force(${input_list$Force[0]})
</#if>
<#if input_list$Delay?size gt 0>
    .delay(${opt.toInt(input_list$Delay[0])})
</#if>
.send();
