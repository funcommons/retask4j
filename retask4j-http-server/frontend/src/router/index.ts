import { createRouter, createWebHistory } from 'vue-router'
import Overview from '@/views/Overview.vue'
import Topics from '@/views/Topics.vue'
import Tasks from '@/views/Tasks.vue'
import Metrics from '@/views/Metrics.vue'
import Alerts from '@/views/Alerts.vue'
import Tester from '@/views/Tester.vue'
import Settings from '@/views/Settings.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview', name: 'overview', component: Overview, meta: { title: 'nav.overview' } },
    { path: '/topics', name: 'topics', component: Topics, meta: { title: 'nav.topics' } },
    { path: '/tasks/:topic?', name: 'tasks', component: Tasks, meta: { title: 'nav.tasks' } },
    { path: '/metrics', name: 'metrics', component: Metrics, meta: { title: 'nav.metrics' } },
    { path: '/alerts', name: 'alerts', component: Alerts, meta: { title: 'nav.alerts' } },
    { path: '/tester', name: 'tester', component: Tester, meta: { title: 'nav.tester' } },
    { path: '/settings', name: 'settings', component: Settings, meta: { title: 'nav.settings' } },
  ],
})

export default router
