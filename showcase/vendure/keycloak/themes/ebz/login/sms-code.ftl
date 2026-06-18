<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        SMS-Code eingeben
    <#elseif section = "form">
        <form id="kc-sms-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <p class="${properties.kcInfoAreaClass!}">
                Wir haben einen Bestätigungscode<#if phone??> an <strong>${phone}</strong></#if> gesendet.
            </p>
            <div class="${properties.kcFormGroupClass!}">
                <label for="code" class="${properties.kcLabelClass!}">Bestätigungscode</label>
                <input type="text" id="code" name="code" class="${properties.kcInputClass!}"
                       inputmode="numeric" autocomplete="one-time-code" autofocus aria-label="Bestätigungscode"/>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}"
                       type="submit" value="Bestätigen"/>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
