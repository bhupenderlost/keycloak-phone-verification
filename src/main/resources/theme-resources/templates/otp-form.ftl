<#import "template.ftl" as layout>

<@layout.registrationLayout displayMessage=true displayInfo=false; section>
    <#if section = "header">
        ${msg("smsAuthOtpTitle")}
    <#elseif section = "form">
        <style>
            #sms-otp-meta {
                display: flex;
                gap: 12px;
                flex-wrap: wrap;
                margin-top: 12px;
            }
        </style>

        <form id="kc-otp-form"
              class="${properties.kcFormClass!}"
              action="${url.loginAction}"
              method="post">

            <div class="${properties.kcFormGroupClass!}">
                <label for="otp" class="${properties.kcLabelClass!}">
                    ${msg("smsAuthOtpLabel")}
                </label>

                <input id="otp"
                       name="otp"
                       type="text"
                       class="${properties.kcInputClass!}"
                       value=""
                       inputmode="numeric"
                       autocomplete="one-time-code"
                       pattern="[0-9]*"
                       maxlength="6"
                       autofocus
                       aria-describedby="sms-otp-meta"
                       aria-invalid="<#if message?has_content && message.type = 'error'>true<#else>false</#if>" />

                <div id="sms-otp-meta" class="${properties.kcInputHelperTextClass!}">
                    <span>${msg("smsAuthCodeExpiresInStatic")}</span>
                    <#if (remainingAttempts)?? && remainingAttempts gte 0>
                        <span id="sms-attempts-remaining">
                            <#if remainingAttempts gt 0>
                                ${msg("smsAuthAttemptsRemaining", remainingAttempts)}
                            <#else>
                                ${msg("smsAuthNoAttemptsRemaining")}
                            </#if>
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           name="submit"
                           id="kc-submit"
                           type="submit"
                           value="${msg('smsAuthVerifyButton')}" />

                    <#if (maxResends)?? && (resendCount)?? && resendCount gte maxResends>
                        <span>${msg("smsAuthNoMoreResends")}</span>
                    <#else>
                        <button class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                id="sms-resend-btn"
                                name="action"
                                type="submit"
                                value="resend">
                            ${msg('smsAuthResendButton')}
                        </button>
                    </#if>
                </div>

                <#if !((maxResends)?? && (resendCount)?? && resendCount gte maxResends)>
                    <span id="sms-resend-cooldown-label" aria-live="polite"></span>
                </#if>
            </div>
        </form>

        <script>
        (function () {
            const i18n = {
                availableIn: ${'"'}${msg("smsAuthAvailableIn")}${'"'},
                verifying: ${'"'}${msg("smsAuthVerifying")}${'"'}
            };

            const otpInput = document.getElementById('otp');
            const form = document.getElementById('kc-otp-form');
            const submitBtn = document.getElementById('kc-submit');
            const resendBtn = document.getElementById('sms-resend-btn');
            const resendLabel = document.getElementById('sms-resend-cooldown-label');
            const cooldownSecs = parseInt('${(cooldownSecs)!'60'}', 10) || 60;
            const cooldownRemainingSecs = parseInt('${(cooldownRemaining)!'-1'}', 10);
            const resendAt = cooldownRemainingSecs >= 0
                ? Date.now() + (cooldownRemainingSecs * 1000)
                : Date.now() + (cooldownSecs * 1000);

            function fmt(secs) {
                const m = Math.floor(secs / 60);
                const s = secs % 60;
                return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
            }

            function syncSubmitState() {
                otpInput.value = otpInput.value.replace(/\D/g, '').slice(0, 6);
                submitBtn.disabled = otpInput.value.length !== 6;
            }

            function updateResendState(resendIn) {
                if (!resendBtn) {
                    return;
                }

                if (resendIn > 0) {
                    resendBtn.disabled = true;
                    resendBtn.setAttribute('disabled', 'disabled');
                    if (resendLabel) {
                        resendLabel.textContent = i18n.availableIn + ' ' + fmt(resendIn);
                    }
                    return;
                }

                resendBtn.disabled = false;
                resendBtn.removeAttribute('disabled');
                if (resendLabel) {
                    resendLabel.textContent = '';
                }
            }

            function tick() {
                const resendIn = Math.max(0, Math.round((resendAt - Date.now()) / 1000));
                updateResendState(resendIn);

                if (resendIn > 0) {
                    window.setTimeout(tick, 1000);
                }
            }

            otpInput.addEventListener('input', syncSubmitState);

            form.addEventListener('submit', function (event) {
                const submitter = event.submitter;
                syncSubmitState();

                if (!submitter || submitter.id !== 'sms-resend-btn') {
                    if (otpInput.value.length !== 6) {
                        event.preventDefault();
                        otpInput.focus();
                        return;
                    }

                    submitBtn.disabled = true;
                    submitBtn.value = i18n.verifying;
                }
            });

            syncSubmitState();
            updateResendState(Math.max(0, Math.round((resendAt - Date.now()) / 1000)));
            tick();
        })();
        </script>
    </#if>
</@layout.registrationLayout>
