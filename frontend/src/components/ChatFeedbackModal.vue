<script setup>
import { ref, computed } from 'vue'
import { submitChatFeedback } from '../api/feedback'

const props = defineProps({
  sessionId: {
    type: Number,
    required: true,
  },
  visible: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'submitted'])

const rating = ref(0)
const hoverRating = ref(0)
const comment = ref('')
const isSubmitting = ref(false)
const submitted = ref(false)
const error = ref('')

const displayRating = computed(() => hoverRating.value || rating.value)

const ratingLabels = {
  1: 'Very Dissatisfied',
  2: 'Dissatisfied',
  3: 'Neutral',
  4: 'Satisfied',
  5: 'Very Satisfied',
}

const currentLabel = computed(() => {
  const r = displayRating.value
  return r > 0 ? ratingLabels[r] : ''
})

function setRating(value) {
  rating.value = value
}

function setHover(value) {
  hoverRating.value = value
}

function clearHover() {
  hoverRating.value = 0
}

async function handleSubmit() {
  if (rating.value < 1 || rating.value > 5) {
    error.value = 'Please select a star rating'
    return
  }

  isSubmitting.value = true
  error.value = ''

  try {
    await submitChatFeedback(props.sessionId, rating.value, comment.value.trim() || null)
    submitted.value = true
    emit('submitted', { rating: rating.value, comment: comment.value })
  } catch (err) {
    error.value = err?.response?.data?.message || 'Failed to submit feedback. Please try again.'
  } finally {
    isSubmitting.value = false
  }
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="visible && !submitted"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="handleClose"
      >
        <div
          class="w-full max-w-md rounded-2xl bg-white shadow-2xl"
          role="dialog"
          aria-label="Rate your experience"
        >
          <!-- Header -->
          <div class="border-b border-slate-200 px-6 py-4">
            <h3 class="text-lg font-semibold text-slate-900">
              How was your experience?
            </h3>
            <p class="mt-1 text-sm text-slate-500">
              Your feedback helps us improve our support
            </p>
          </div>

          <!-- Content -->
          <div class="px-6 py-5">
            <!-- Star Rating -->
            <div class="text-center">
              <div class="flex justify-center gap-2">
                <button
                  v-for="star in 5"
                  :key="star"
                  type="button"
                  class="group focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 rounded-full"
                  @click="setRating(star)"
                  @mouseenter="setHover(star)"
                  @mouseleave="clearHover"
                  :aria-label="`Rate ${star} star${star !== 1 ? 's' : ''}`"
                >
                  <svg
                    class="h-10 w-10 transition-all duration-150"
                    :class="star <= displayRating ? 'text-amber-400 scale-110' : 'text-slate-300'"
                    fill="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
                    />
                  </svg>
                </button>
              </div>
              <p
                v-if="currentLabel"
                class="mt-3 text-sm font-medium text-slate-700"
              >
                {{ currentLabel }}
              </p>
            </div>

            <!-- Comment -->
            <div class="mt-5">
              <label
                for="feedback-comment"
                class="block text-sm font-medium text-slate-700"
              >
                Additional feedback (optional)
              </label>
              <textarea
                id="feedback-comment"
                v-model="comment"
                rows="3"
                maxlength="500"
                placeholder="Tell us more about your experience..."
                class="mt-1 block w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              ></textarea>
            </div>

            <!-- Error -->
            <p
              v-if="error"
              class="mt-3 text-sm text-red-600"
            >
              {{ error }}
            </p>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4">
            <button
              type="button"
              class="rounded-xl px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-100"
              @click="handleClose"
            >
              Skip
            </button>
            <button
              type="button"
              :disabled="rating < 1 || isSubmitting"
              class="rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 px-5 py-2 text-sm font-medium text-white shadow-md transition hover:shadow-lg active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
              @click="handleSubmit"
            >
              {{ isSubmitting ? 'Submitting...' : 'Submit' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Success state -->
    <Transition name="modal">
      <div
        v-if="visible && submitted"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="handleClose"
      >
        <div
          class="w-full max-w-sm rounded-2xl bg-white p-8 text-center shadow-2xl"
          role="dialog"
          aria-label="Thank you"
        >
          <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100">
            <svg
              class="h-8 w-8 text-emerald-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="2"
              stroke="currentColor"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M4.5 12.75l6 6 9-13.5"
              />
            </svg>
          </div>
          <h3 class="mt-4 text-lg font-semibold text-slate-900">
            Thank you!
          </h3>
          <p class="mt-2 text-sm text-slate-500">
            Your feedback helps us improve our support experience.
          </p>
          <button
            type="button"
            class="mt-6 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 px-6 py-2.5 text-sm font-medium text-white shadow-md transition hover:shadow-lg active:scale-95"
            @click="handleClose"
          >
            Done
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
