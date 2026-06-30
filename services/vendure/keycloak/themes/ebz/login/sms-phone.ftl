<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        Mobilnummer bestätigen
    <#elseif section = "form">
        <form id="kc-sms-phone-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <p class="${properties.kcInfoAreaClass!}">Wir senden Ihnen einen Bestätigungscode per SMS.</p>
            <div class="${properties.kcFormGroupClass!}">
                <label for="phoneNumber" class="${properties.kcLabelClass!}">Mobilnummer</label>
                <input type="tel" id="phoneNumber" name="phoneNumber" class="${properties.kcInputClass!}"
                       placeholder="+49 151 1234567" autofocus aria-label="Mobilnummer"/>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}"
                       type="submit" value="Code per SMS anfordern"/>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
