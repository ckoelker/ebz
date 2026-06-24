<script setup lang="ts">
import { computed } from 'vue';
import { initialen } from '../domain/party';

// Avatar für Person (rund) oder Firma (eckig) mit Initialen. EINE Quelle, geteilt über @crm-ui:
// nutzt die global registrierte Nuxt-UI-Komponente <UAvatar> (vue-only-Plugin) — keine App-Bindung.
const props = withDefaults(defineProps<{
  name: string;
  org?: boolean;
  size?: '3xs' | '2xs' | 'xs' | 'sm' | 'md' | 'lg' | 'xl';
}>(), { size: 'sm' });

const rounding = computed(() =>
  props.org ? (props.size === 'lg' || props.size === 'xl' ? 'rounded-lg' : 'rounded-md') : '');
</script>

<template>
  <UAvatar :text="initialen(name)" :size="size" :class="rounding" />
</template>
