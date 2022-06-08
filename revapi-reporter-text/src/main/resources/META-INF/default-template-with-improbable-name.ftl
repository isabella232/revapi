<#--

    Copyright 2014-2020 Lukas Krejci
    and other contributors as indicated by the @author tags.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
Analysis results
----------------

Old API: <#list analysis.oldApi.archives as archive>${archive.name}<#sep>, </#list>
New API: <#list analysis.newApi.archives as archive>${archive.name}<#sep>, </#list>
<#list reports as report>
old: ${report.oldElement!"<none>"}
new: ${report.newElement!"<none>"}
<#list report.differences as diff>
${diff.code}<#if diff.description??>: ${diff.description}</#if>
<#if diff.justification??>
${diff.justification}
</#if>
<#list diff.classification?keys as compat>${compat}: ${diff.classification?api.get(compat)}<#sep>, </#list>
</#list>
<#sep>

</#sep>
</#list>
<#-- force an empty line at the end -->
<#nt/>
<#t/>
