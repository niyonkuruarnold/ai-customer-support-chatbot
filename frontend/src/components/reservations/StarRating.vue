<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** Current rating value (1-5) */
  modelValue: {
    type: Number,
    default: 0,
  },
  /** Maximum number of stars */
  maxStars: {
    type: Number,
    default: 5,
  },
  /** Whether the component is in read-only mode */
  readonly: {
    type: Boolean,
    default: false,
  },
  /** Size variant: 'sm', 'md', 'lg' */
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
})

const emit = defineEmits(['update:modelValue'])

const hoverRating = defineModel('hover', { default: 0 })

const stars = computed(() => {
  const displayRating = hoverRating.value || props.modelValue
  return Array.from({ length: props.maxStars }, (_, i) => ({
    index: i + 1,
    filled: i + 1 <= displayRating,
  }))
})

const sizeClasses = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'size-4'
    case 'lg':
      return 'size-7'
    default:
      return 'size-5'
  }
})

function setRating(value) {
  if (!props.readonly) {
    emit('update:modelValue', value)
  }
}

function handleMouseEnter(value) {
  if (!props.readonly) {
    hoverRating.value = value
  }
}

function handleMouseLeave() {
  hoverRating.value = 0
}
</script>

<template>
  <div
    class="inline-flex items-center gap-0.5"
    :class="{ 'cursor-pointer': !readonly }"
    @mouseleave="handleMouseLeave"
  >
    <button
      v-for="star in stars"
      :key="star.index"
      type="button"
      :disabled="readonly"
      :class="[
        sizeClasses,
        'transition-colors duration-150',
        readonly ? 'cursor-default' : 'cursor-pointer hover:scale-110',
      ]"
      @click="setRating(star.index)"
      @mouseenter="handleMouseEnter(star.index)"
      :aria-label="`Rate ${star.index} out of ${maxStars}`"
    >
      <svg
        viewBox="0 0 24 24"
        :fill="star.filled ? 'currentColor' : 'none'"
        :stroke="star.filled ? 'none' : 'currentColor'"
        stroke-width="1.5"
        :class="star.filled ? 'text-amber-400' : 'text-slate-300'"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M11.48 3.499a.562.562 0 0 1 1.04 0l2.125 5.111a.563.563 0 0 0 .475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 0 0-.182.557l1.285 5.385a.562.562 0 0 1-.84.61l-4.725-2.885a.562.562 0 0 0-.586 0L6.982 20.54a.562.562 0 0 1-.84-.61l1.285-5.386a.562.562 0 0 0-.182-.557l-4.204-3.602a.562.562 0 0 1 .321-.988l5.518-.442a.563.563 0 0 0 .475-.345L11.48 3.5Z"
        />
      </svg>
    </button>
  </div>
</template>
