<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { Kontaktpunkt, KontaktTyp, Kontext } from '../../domain/types'
import { kontaktIcon } from '../../domain/kontaktpunkt'

// Inline-Pflege der Kontaktkanäle (E-Mail/Telefon/Adresse): hinzufügen, bearbeiten,
// löschen, Primär-Flag, Kontext (privat/dienstlich). Genau ein Primärkanal je Typ.
const props = defineProps<{ kanaele: Kontaktpunkt[]; mitKontext?: boolean }>()
const emit = defineEmits<{ (e: 'change', v: Kontaktpunkt[]): void }>()

const liste = ref<Kontaktpunkt[]>(props.kanaele.map(k => ({ ...k })))
const neu = reactive<{ typ: KontaktTyp; wert: string; kontext: Kontext }>({ typ: 'EMAIL', wert: '', kontext: 'dienstlich' })
const typItems = [
  { label: 'E-Mail', value: 'EMAIL' }, { label: 'Telefon', value: 'TELEFON' }, { label: 'Adresse', value: 'ADRESSE' },
]
const kontextItems = [{ label: 'privat', value: 'privat' }, { label: 'dienstlich', value: 'dienstlich' }]

function anzeige(k: Kontaktpunkt) {
  return k.email ?? k.nummerAnzeige ?? [k.strasse, k.hausnummer, k.plz, k.ort].filter(Boolean).join(' ')
}
function add() {
  if (!neu.wert.trim()) return
  const k: Kontaktpunkt = { typ: neu.typ, primaer: false, status: 'AKTIV', kontext: props.mitKontext ? neu.kontext : undefined }
  if (neu.typ === 'EMAIL') k.email = neu.wert
  else if (neu.typ === 'TELEFON') k.nummerAnzeige = neu.wert
  else k.strasse = neu.wert
  liste.value.push(k)
  neu.wert = ''
  emit('change', liste.value)
}
function del(i: number) { liste.value.splice(i, 1); emit('change', liste.value) }
function primaer(i: number) {
  const typ = liste.value[i].typ
  liste.value.forEach((k, j) => { if (k.typ === typ) k.primaer = j === i })
  emit('change', liste.value)
}
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="(k, i) in liste"
      :key="i"
      class="flex items-center gap-2 px-3 py-2 rounded-md ring-1 ring-default bg-default"
    >
      <UIcon :name="kontaktIcon(k.typ)" class="size-4 text-muted shrink-0" />
      <span class="text-sm text-default truncate flex-1">{{ anzeige(k) }}</span>
      <UBadge v-if="mitKontext && k.kontext" color="neutral" variant="subtle" size="sm">{{ k.kontext }}</UBadge>
      <UButton
        :color="k.primaer ? 'primary' : 'neutral'"
        :variant="k.primaer ? 'soft' : 'ghost'"
        size="sm"
        :icon="k.primaer ? 'i-lucide-star' : 'i-lucide-star-off'"
        :title="k.primaer ? 'Primärkanal' : 'als Primär setzen'"
        @click="primaer(i)"
      />
      <UButton color="error" variant="ghost" size="sm" icon="i-lucide-trash-2" @click="del(i)" />
    </div>

    <div class="flex items-end gap-2 pt-1">
      <USelect v-model="neu.typ" :items="typItems" size="sm" class="w-28" />
      <UInput v-model="neu.wert" size="sm" placeholder="Wert …" class="flex-1" @keyup.enter="add" />
      <USelect v-if="mitKontext" v-model="neu.kontext" :items="kontextItems" size="sm" class="w-28" />
      <UButton color="primary" variant="soft" size="sm" icon="i-lucide-plus" @click="add">Hinzufügen</UButton>
    </div>
  </div>
</template>
