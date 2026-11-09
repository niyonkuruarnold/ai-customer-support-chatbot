import { createApp } from 'vue'
import { createPinia } from 'pinia'
import axios from 'axios'
import './style.css'
import App from './App.vue'
import { attachUnauthorizedHandler } from './api/authToken'

attachUnauthorizedHandler(axios)
createApp(App).use(createPinia()).mount('#app')
