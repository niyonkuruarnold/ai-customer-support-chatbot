<script setup>
import StarRating from './StarRating.vue'

const props = defineProps({
  /** List of reviews to display */
  reviews: {
    type: Array,
    default: () => [],
  },
  /** Average rating object { averageRating, reviewCount } */
  averageRating: {
    type: Object,
    default: null,
  },
  /** Whether reviews are loading */
  isLoading: {
    type: Boolean,
    default: false,
  },
})

function formatDate(timestamp) {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString([], {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
}
</script>

<template>
  <div>
    <!-- Average rating summary -->
    <div
      v-if="averageRating && averageRating.reviewCount > 0"
      class="mb-4 flex items-center gap-3 rounded-lg bg-amber-50 p-3"
    >
      <div class="text-center">
        <div class="text-2xl font-bold text-amber-600">
          {{ averageRating.averageRating.toFixed(1) }}
        </div>
        <StarRating :model-value="Math.round(averageRating.averageRating)" readonly size="sm" />
      </div>
      <div class="text-sm text-slate-600">
        <span class="font-medium">{{ averageRating.reviewCount }}</span>
        {{ averageRating.reviewCount === 1 ? 'review' : 'reviews' }}
      </div>
    </div>

    <!-- No reviews state -->
    <div
      v-if="!isLoading && reviews.length === 0"
      class="rounded-lg border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-400"
    >
      <p>No reviews yet. Be the first to review this tool!</p>
    </div>

    <!-- Loading state -->
    <div
      v-if="isLoading"
      class="flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400"
    >
      <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      Loading reviews...
    </div>

    <!-- Review list -->
    <div v-else-if="reviews.length > 0" class="space-y-3">
      <div
        v-for="review in reviews"
        :key="review.id"
        class="rounded-lg border border-slate-200 bg-white p-4"
      >
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-2">
            <StarRating :model-value="review.rating" readonly size="sm" />
            <span class="text-xs text-slate-500">
              User #{{ review.reviewerId }}
            </span>
          </div>
          <span class="text-xs text-slate-400">
            {{ formatDate(review.timestamp) }}
          </span>
        </div>
        <p
          v-if="review.comment"
          class="mt-2 text-sm leading-relaxed text-slate-700"
        >
          {{ review.comment }}
        </p>
        <p v-else class="mt-2 text-xs italic text-slate-400">
          No comment provided
        </p>
      </div>
    </div>
  </div>
</template>
