<script setup lang="ts">
import { ref, computed, watch } from 'vue';

// Standard-Rahmen für Dialoge: UModal + Footer (Abbrechen + Primäraktion). Übernommen aus der
// CRM-Kernmaske-Storybook-Abnahme (gleiche Optik), hier ohne Storybook-Typabhängigkeit.
const props = withDefaults(defineProps<{
  title: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  open?: boolean;
  primaryLabel?: string;
  primaryIcon?: string;
  primaryColor?: string;
  primaryLoading?: boolean;
  primaryDisabled?: boolean;
  cancelLabel?: string;
}>(), {
  size: 'md',
  primaryColor: 'primary',
  cancelLabel: 'Abbrechen',
});
const emit = defineEmits<{ (e: 'primary'): void; (e: 'update:open', v: boolean): void }>();

const isOpen = ref(props.open ?? false);
watch(() => props.open, (v) => { if (v !== undefined) isOpen.value = v; });
watch(isOpen, (v) => emit('update:open', v));

const maxW = computed(() => ({ sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-xl', xl: 'max-w-3xl' }[props.size]));
</script>

<template>
  <UModal v-model:open="isOpen" :title="title" :ui="{ content: maxW }">
    <template #body>
      <slot />
    </template>
    <template #footer>
      <div class="flex items-center w-full gap-2">
        <slot name="footer-start" />
        <div class="ml-auto flex gap-2">
          <UButton color="neutral" variant="ghost" @click="isOpen = false">{{ cancelLabel }}</UButton>
          <UButton
            v-if="primaryLabel"
            :color="primaryColor"
            :icon="primaryIcon"
            :loading="primaryLoading"
            :disabled="primaryDisabled"
            @click="emit('primary')"
          >
            {{ primaryLabel }}
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>
