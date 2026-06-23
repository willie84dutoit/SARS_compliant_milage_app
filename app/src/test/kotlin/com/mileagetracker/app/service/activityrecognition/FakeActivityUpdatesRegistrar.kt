package com.mileagetracker.app.service.activityrecognition

/**
 * Hand-written fake (no mocking framework, per this project's testing convention) for
 * [ActivityUpdatesRegistrar], used by [ConfidenceAcquisitionWindowTest] to assert the
 * idempotent-registration and unregister-discipline guarantees from
 * `team/blueprints/T-002-vehicle-detection-spec.md` §4/§5 without touching the real
 * `ActivityRecognitionClient`.
 */
class FakeActivityUpdatesRegistrar : ActivityUpdatesRegistrar {

    var registerCallCount: Int = 0
        private set

    var unregisterCallCount: Int = 0
        private set

    override fun register() {
        registerCallCount += 1
    }

    override fun unregister() {
        unregisterCallCount += 1
    }
}
