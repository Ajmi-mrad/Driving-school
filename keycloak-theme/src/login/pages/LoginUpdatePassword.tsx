import { useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { getKcClsx, type KcClsx } from "keycloakify/login/lib/kcClsx";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { JSX } from "keycloakify/tools/JSX";
import { useIsPasswordRevealed } from "keycloakify/tools/useIsPasswordRevealed";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";

/**
 * Update-password page with a live strength meter + rule checklist.
 * Rules mirror the realm password policy:
 *   length(8) and upperCase(1) and specialChars(1) and digits(1)
 * The bar turns green only when all four rules pass; the primary submit is
 * disabled until the password is strong and the confirmation matches.
 * (The Keycloak policy remains the authoritative server-side gate.)
 */
export default function LoginUpdatePassword(
    props: PageProps<Extract<KcContext, { pageId: "login-update-password.ftl" }>, I18n>
) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { msg, msgStr } = i18n;

    const { url, messagesPerField, isAppInitiatedAction } = kcContext;

    const [password, setPassword] = useState("");
    const [passwordConfirm, setPasswordConfirm] = useState("");

    const rules = getRules(password);
    const metCount = rules.filter(r => r.met).length;
    const isStrong = metCount === rules.length;
    const isMatching = passwordConfirm.length > 0 && passwordConfirm === password;
    const canSubmit = isStrong && isMatching;

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayMessage={!messagesPerField.existsError("password", "password-confirm")}
            headerNode={msg("updatePasswordTitle")}
        >
            <form id="kc-passwd-update-form" className={kcClsx("kcFormClass")} action={url.loginAction} method="post">
                <div className={kcClsx("kcFormGroupClass")}>
                    <div className={kcClsx("kcLabelWrapperClass")}>
                        <label htmlFor="password-new" className={kcClsx("kcLabelClass")}>
                            {msg("passwordNew")}
                        </label>
                    </div>
                    <div className={kcClsx("kcInputWrapperClass")}>
                        <PasswordWrapper kcClsx={kcClsx} i18n={i18n} passwordInputId="password-new">
                            <input
                                type="password"
                                id="password-new"
                                name="password-new"
                                className={kcClsx("kcInputClass")}
                                autoFocus
                                autoComplete="new-password"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                aria-invalid={messagesPerField.existsError("password", "password-confirm")}
                            />
                        </PasswordWrapper>

                        <PasswordStrength i18n={i18n} password={password} rules={rules} metCount={metCount} />

                        {messagesPerField.existsError("password") && (
                            <span
                                id="input-error-password"
                                className={kcClsx("kcInputErrorMessageClass")}
                                aria-live="polite"
                                dangerouslySetInnerHTML={{
                                    __html: kcSanitize(messagesPerField.get("password"))
                                }}
                            />
                        )}
                    </div>
                </div>

                <div className={kcClsx("kcFormGroupClass")}>
                    <div className={kcClsx("kcLabelWrapperClass")}>
                        <label htmlFor="password-confirm" className={kcClsx("kcLabelClass")}>
                            {msg("passwordConfirm")}
                        </label>
                    </div>
                    <div className={kcClsx("kcInputWrapperClass")}>
                        <PasswordWrapper kcClsx={kcClsx} i18n={i18n} passwordInputId="password-confirm">
                            <input
                                type="password"
                                id="password-confirm"
                                name="password-confirm"
                                className={kcClsx("kcInputClass")}
                                autoComplete="new-password"
                                value={passwordConfirm}
                                onChange={e => setPasswordConfirm(e.target.value)}
                                aria-invalid={messagesPerField.existsError("password", "password-confirm")}
                            />
                        </PasswordWrapper>

                        {passwordConfirm.length > 0 && !isMatching && (
                            <span className="ae-pw-mismatch" aria-live="polite">
                                {tr(i18n, "noMatch")}
                            </span>
                        )}

                        {messagesPerField.existsError("password-confirm") && (
                            <span
                                id="input-error-password-confirm"
                                className={kcClsx("kcInputErrorMessageClass")}
                                aria-live="polite"
                                dangerouslySetInnerHTML={{
                                    __html: kcSanitize(messagesPerField.get("password-confirm"))
                                }}
                            />
                        )}
                    </div>
                </div>
                <div className={kcClsx("kcFormGroupClass")}>
                    <LogoutOtherSessions kcClsx={kcClsx} i18n={i18n} />
                    <div id="kc-form-buttons" className={kcClsx("kcFormButtonsClass")}>
                        <input
                            className={kcClsx(
                                "kcButtonClass",
                                "kcButtonPrimaryClass",
                                !isAppInitiatedAction && "kcButtonBlockClass",
                                "kcButtonLargeClass"
                            )}
                            type="submit"
                            disabled={!canSubmit}
                            value={msgStr("doSubmit")}
                        />
                        {isAppInitiatedAction && (
                            <button
                                className={kcClsx("kcButtonClass", "kcButtonDefaultClass", "kcButtonLargeClass")}
                                type="submit"
                                name="cancel-aia"
                                value="true"
                            >
                                {msg("doCancel")}
                            </button>
                        )}
                    </div>
                </div>
            </form>
        </Template>
    );
}

// -- Strength model --------------------------------------------------------

type Rule = { key: "length" | "upper" | "special" | "digit"; met: boolean };

function getRules(password: string): Rule[] {
    return [
        { key: "length", met: password.length >= 8 },
        { key: "upper", met: /[A-Z]/.test(password) },
        { key: "special", met: /[^A-Za-z0-9]/.test(password) },
        { key: "digit", met: /[0-9]/.test(password) }
    ];
}

function PasswordStrength(props: { i18n: I18n; password: string; rules: Rule[]; metCount: number }) {
    const { i18n, password, rules, metCount } = props;

    // level: 0 (empty) .. 4 (strong). Bar colour keyed on level via data-attr.
    const level = password.length === 0 ? 0 : metCount;
    const percent = (metCount / rules.length) * 100;

    if (password.length === 0) {
        return <PasswordRules i18n={i18n} rules={rules} />;
    }

    return (
        <div className="ae-pw" aria-live="polite">
            <div className="ae-pw-bar">
                <div className="ae-pw-bar-fill" data-level={level} style={{ width: `${percent}%` }} />
            </div>
            <div className="ae-pw-label" data-level={level}>
                {tr(i18n, "strength")}: {tr(i18n, strengthWord(metCount))}
            </div>
            <PasswordRules i18n={i18n} rules={rules} />
        </div>
    );
}

function PasswordRules(props: { i18n: I18n; rules: Rule[] }) {
    const { i18n, rules } = props;
    return (
        <ul className="ae-pw-rules">
            {rules.map(rule => (
                <li key={rule.key} className={rule.met ? "ae-pw-rule met" : "ae-pw-rule"}>
                    <span className="ae-pw-rule-icon" aria-hidden>
                        {rule.met ? "✓" : "○"}
                    </span>
                    {tr(i18n, rule.key)}
                </li>
            ))}
        </ul>
    );
}

function strengthWord(metCount: number): TrKey {
    if (metCount <= 1) return "weak";
    if (metCount === 2) return "fair";
    if (metCount === 3) return "good";
    return "strong";
}

// -- Minimal localisation for the meter (en/fr/ar, en fallback) ------------

type TrKey =
    | "length"
    | "upper"
    | "special"
    | "digit"
    | "strength"
    | "weak"
    | "fair"
    | "good"
    | "strong"
    | "noMatch";

const TRANSLATIONS: Record<"en" | "fr" | "ar", Record<TrKey, string>> = {
    en: {
        length: "At least 8 characters",
        upper: "An uppercase letter",
        special: "A special character",
        digit: "A number",
        strength: "Password strength",
        weak: "Weak",
        fair: "Fair",
        good: "Good",
        strong: "Strong",
        noMatch: "Passwords do not match"
    },
    fr: {
        length: "Au moins 8 caractères",
        upper: "Une lettre majuscule",
        special: "Un caractère spécial",
        digit: "Un chiffre",
        strength: "Robustesse du mot de passe",
        weak: "Faible",
        fair: "Moyen",
        good: "Bon",
        strong: "Fort",
        noMatch: "Les mots de passe ne correspondent pas"
    },
    ar: {
        length: "8 أحرف على الأقل",
        upper: "حرف كبير واحد",
        special: "رمز خاص واحد",
        digit: "رقم واحد",
        strength: "قوة كلمة المرور",
        weak: "ضعيفة",
        fair: "متوسطة",
        good: "جيدة",
        strong: "قوية",
        noMatch: "كلمتا المرور غير متطابقتين"
    }
};

function tr(i18n: I18n, key: TrKey): string {
    const tag = i18n.currentLanguage.languageTag;
    const set = tag === "fr" ? TRANSLATIONS.fr : tag === "ar" ? TRANSLATIONS.ar : TRANSLATIONS.en;
    return set[key];
}

// -- Unchanged helpers from the default page -------------------------------

function LogoutOtherSessions(props: { kcClsx: KcClsx; i18n: I18n }) {
    const { kcClsx, i18n } = props;

    const { msg } = i18n;

    return (
        <div id="kc-form-options" className={kcClsx("kcFormOptionsClass")}>
            <div className={kcClsx("kcFormOptionsWrapperClass")}>
                <div className="checkbox">
                    <label>
                        <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" />
                        {msg("logoutOtherSessions")}
                    </label>
                </div>
            </div>
        </div>
    );
}

function PasswordWrapper(props: { kcClsx: KcClsx; i18n: I18n; passwordInputId: string; children: JSX.Element }) {
    const { kcClsx, i18n, passwordInputId, children } = props;

    const { msgStr } = i18n;

    const { isPasswordRevealed, toggleIsPasswordRevealed } = useIsPasswordRevealed({ passwordInputId });

    return (
        <div className={kcClsx("kcInputGroup")}>
            {children}
            <button
                type="button"
                className={kcClsx("kcFormPasswordVisibilityButtonClass")}
                aria-label={msgStr(isPasswordRevealed ? "hidePassword" : "showPassword")}
                aria-controls={passwordInputId}
                onClick={toggleIsPasswordRevealed}
            >
                <i className={kcClsx(isPasswordRevealed ? "kcFormPasswordVisibilityIconHide" : "kcFormPasswordVisibilityIconShow")} aria-hidden />
            </button>
        </div>
    );
}