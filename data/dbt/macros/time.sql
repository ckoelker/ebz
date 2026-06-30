{#- Monats-Bucketing in Europe/Berlin (L15). Vendure speichert `timestamp without time zone`
    als UTC → erst als UTC interpretieren, dann nach Berlin wandeln (sonst Off-by-one an
    Monatsgrenzen). Für `timestamptz`-Spalten (HubSpot) is_utc_naive=false. -#}
{% macro to_berlin(col, is_utc_naive=true) -%}
{%- if is_utc_naive -%}(({{ col }}) at time zone 'UTC' at time zone 'Europe/Berlin')
{%- else -%}(({{ col }}) at time zone 'Europe/Berlin'){%- endif -%}
{%- endmacro %}

{% macro month_berlin(col, is_utc_naive=true) -%}
date_trunc('month', {{ to_berlin(col, is_utc_naive) }})::date
{%- endmacro %}

{#- Anker-Monat für die Forecast-Zuordnung von Pipeline/gewonnenen Deals.
    var('forecast_anchor') leer => current_date. -#}
{% macro anchor_month() -%}
{%- set a = var('forecast_anchor', '') -%}
{%- if a == '' -%}date_trunc('month', current_date)::date
{%- else -%}date_trunc('month', date '{{ a }}')::date{%- endif -%}
{%- endmacro %}
