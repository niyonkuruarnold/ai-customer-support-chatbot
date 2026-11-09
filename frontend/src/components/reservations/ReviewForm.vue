<script setup>
import { ref } from 'vue'
import StarRating from './StarRating.vue'

const props = defineProps({
  /** Tool ID being reviewed */
  toolId: {
    type: Number,
    required: true,
  },
  /** Reservation ID being reviewed */
  reservationId: {
    type: Number,
    required: true,
  },
  /** Whether the form is currently submitting */
  isSubmitting: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['submit', 'cancel'])

const rating = ref(0)
const comment = ref('')
const error = ref('')

function handleSubmit() {
  error.value = ''

  if (rating.value < 1 || rating.value > 5) {
    error.value = 'Please select a star rating'
    return
  }

  if (comment.value.length > 500) {
    error.value = 'Comment must be 500 characters or less'
    return
  }

  emit('submit', {
    toolId: props.toolId,
    reservationId: props.reservationId,
    rating: rating.value,
    comment: comment.value.trim() || null,
  })
}

function handleCancel() {
  rating.value = 0
  comment.value = ''
  error.value = ''
  emit('cancel')
}
</script>

<template>
  <div class="rounded-xl border border-indigo-200 bg-indigo-50 p-4">
    <h4 class="text-sm font-semibold text-indigo-800">Leave a Review</h4>
    <p class="mt-1 text-xs text-indigo-600">
      How was your experience borrowing this tool?
    </p>

    <form @submit.prevent="handleSubmit" class="mt-3 space-y-3">
      <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">
          Rating
        </label>
        <StarRating v-model="rating" size="lg" />
        <p class="mt-1 text-xs text-slate-500">
          {{ rating === 0 ? 'Click a star to rate' : `${rating} out of 5` }}
        </p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">
          Comment (optional)
        </label>
        <textarea
          v-model="comment"
          rows="3"
          maxlength="500"
          placeholder="Tell others about your experience with this tool..."
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
        ></textarea>
        <p class="mt-1 text-right text-xs text-slate-400">
          {{ comment.length }}/500
        </p>
      </div>

      <p v-if="error" class="text-sm text-red-600" role="alert">
        {{ error }}
      </p>

      <div class="flex gap-2">
        <button
          type="submit"
          :disabled="isSubmitting || rating === 0"
          class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ isSubmitting ? 'Submitting...' : 'Submit Review' }}
        </button>
        <button
          type="button"
          @click="handleCancel"
          :disabled="isSubmitting"
          class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-50"
        >
          Cancel
        </button>
      </div>
    </form>
  </div>
</template>
