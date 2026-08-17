SummonFXBlockClient.create(${input$X}, ${input$Y}, ${input$Z})
.name("${field$Name}")
<#if input_list$OffSetX?size gt 0>
    .offset(${input_list$OffSetX[0]}, ${input_list$OffSetY[0]}, ${input_list$OffSetZ[0]})
</#if>
<#if input_list$RotationX?size gt 0>
    .rotation(${input_list$RotationX[0]}, ${input_list$RotationY[0]}, ${input_list$RotationZ[0]})
</#if>
<#if input_list$ScaleX?size gt 0>
    .scale(${input_list$ScaleX[0]}, ${input_list$ScaleY[0]}, ${input_list$ScaleZ[0]})
</#if>
<#if input_list$Delay?size gt 0>
    .delay(${input_list$Delay[0]})
</#if>
<#if input_list$Death?size gt 0>
    .forcedDeath(${input_list$Death[0]})
</#if>
<#if input_list$Multi?size gt 0>
    .allowMulti(${input_list$Multi[0]})
</#if>
<#if input_list$State?size gt 0>
    .checkState(${input_list$State[0]})
</#if>
.send();
