// Only what lib/telemetry.ts uses: importing @sentry/browser whole would also bundle session replay and feedback.
export { captureException, init, setUser } from '@sentry/browser'
