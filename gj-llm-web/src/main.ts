import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from '@/router'
import App from '@/App.vue'
import { setupElementPlus } from '@/plugins/element-plus'
import { setupDirectives } from '@/directives'
import '@/styles/global.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
setupElementPlus(app)
setupDirectives(app)

app.mount('#app')
