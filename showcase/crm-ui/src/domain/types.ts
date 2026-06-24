// Minimale, app-neutrale Typen für die geteilten CRM-Primitive. Bewusst klein gehalten:
// fachliche Domänen-Typen (Person/Organisation/…) bleiben in der jeweiligen App.
export type Health = 'ok' | 'warn' | 'err';
export type UiColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral';
