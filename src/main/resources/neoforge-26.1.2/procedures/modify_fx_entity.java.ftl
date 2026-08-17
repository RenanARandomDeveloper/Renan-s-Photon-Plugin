ModifyFXEntityServer.modify(${input$Entity})
<#if field_list$Name?size gt 0>
    .name("${field_list$Name[0]}")
</#if>
<#if input_list$OffSetX?size gt 0>
    .offset(${input_list$OffSetX[0]}, ${input_list$OffSetY[0]}, ${input_list$OffSetZ[0]})
</#if>
<#if input_list$RotationX?size gt 0>
    .rotation(${input_list$RotationX[0]}, ${input_list$RotationY[0]}, ${input_list$RotationZ[0]})
</#if>
<#if input_list$ScaleX?size gt 0>
    .scale(${input_list$ScaleX[0]}, ${input_list$ScaleY[0]}, ${input_list$ScaleZ[0]})
</#if>
.send();