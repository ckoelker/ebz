import { useQuery } from '@tanstack/vue-query';
import { getCrmLookupsKategorie } from '@/api/endpoints/crm-resource/crm-resource';
import type { LookupView } from '@/api/model';

// Lädt eine CRM-Lookup-Liste (Rollen, Länder, Branchen …) über die generische /crm/lookups-API.
// Lange staleTime: Stammdaten ändern sich selten; ein Cache je Kategorie reicht fürs ganze Cockpit.
export function useLookup(kategorie: string) {
  return useQuery({
    queryKey: ['crm-lookup', kategorie],
    queryFn: async (): Promise<LookupView[]> => (await getCrmLookupsKategorie(kategorie)) ?? [],
    staleTime: 1000 * 60 * 30,
  });
}

// Nuxt-UI-USelect-Items aus einer Lookup-Liste (Wert = Code, Label = Bezeichnung).
export function lookupItems(values: LookupView[] | undefined) {
  return (values ?? []).map((l) => ({ label: l.bezeichnung ?? l.code ?? '', value: l.code ?? '' }));
}
