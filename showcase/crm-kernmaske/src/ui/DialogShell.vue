<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { UiColor } from '../domain/severity'

// Standard-Rahmen für Dialoge: Trigger-Button + UModal + Footer (optionaler
// Links-Slot, Abbrechen, Primäraktion). Ersetzt den 6× duplizierten Modal-Boilerplate.
const props = withDefaults(defineProps<{
  title: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  open?: boolean
  triggerLabel?: string
  triggerIcon?: string
  triggerColor?: UiColor
  triggerVariant?: 'solid' | 'soft' | 'outline' | 'ghost'
  primaryLabel?: string
  primaryIcon?: string
  primaryColor?: UiColor
  primaryDisabled?: boolean
  cancelLabel?: string
}>(), {
  size: 'md', triggerColor: 'primary', triggerVariant: 'solid',
  primaryColor: 'primary', cancelLabel: 'Abbrechen',
})
const emit = defineEmits<{ (e: 'primary'): void; (e: 'update:open', v: boolean): void }>()

const isOpen = ref(props.open ?? false)
watch(() => props.open, v => { if (v !== undefined) isOpen.value = v })
watch(isOpen, v => emit('update:open', v))

const maxW = computed(() => ({ sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-xl', xl: 'max-w-2xl' }[props.size]))
function onPrimary() { emit('primary'); isOpen.value = false }
</script>

<template>
  <div>
    <UButton
      v-if="triggerLabel || triggerIcon"
      :color="triggerColor"
      :variant="triggerVariant"
      :icon="triggerIcon"
      @click="isOpen = true"
    >
      {{ triggerLabel }}
    </UButton>

    <UModal v-model:open="isOpen" :title="title" :ui="{ content: maxW }">
      <template #body>
        <slot />
      </template>
      <template #footer>
        <!-- #footer überschreibt den Standardfuß (z. B. für Wizards); sonst Abbrechen + Primär. -->
        <slot name="footer" :close="() => (isOpen = false)">
          <div class="flex items-center w-full gap-2">
            <slot name="footer-start" />
            <div class="ml-auto flex gap-2">
              <UButton color="neutral" variant="ghost" @click="isOpen = false">{{ cancelLabel }}</UButton>
              <UButton
                v-if="primaryLabel"
                :color="primaryColor"
                :icon="primaryIcon"
                :disabled="primaryDisabled"
                @click="onPrimary"
              >
                {{ primaryLabel }}
              </UButton>
            </div>
          </div>
        </slot>
      </template>
    </UModal>
  </div>
</template>
