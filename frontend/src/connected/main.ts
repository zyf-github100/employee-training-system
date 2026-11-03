// Preserve bookmarks to the former management workspace after migration.
const legacyRoutes: Record<string, string> = {
  courses: 'courses',
  assessment: 'exams',
  plans: 'plans',
  organization: 'employees',
  archive: 'analytics',
  certificates: 'certificates',
  profile: 'profile'
}
const legacyView = window.location.hash.replace(/^#\/?/, '')
window.location.replace(`./#/admin/${legacyRoutes[legacyView] || 'dashboard'}`)
