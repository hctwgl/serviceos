@preconcurrency import CoreLocation
import Foundation
import TechnicianIOSFoundation

enum TechnicianLocationError: Error {
    case permissionDenied
    case unavailable
    case alreadyRequesting
}

/// 只响应用户主动动作的一次定位请求；不会启动持续定位、后台定位或位置历史缓存。
@MainActor
final class OneShotLocationProvider: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager: CLLocationManager
    private var continuation: CheckedContinuation<TechnicianCapturedLocation, Error>?

    override init() {
        manager = CLLocationManager()
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func capture() async throws -> TechnicianCapturedLocation {
        guard continuation == nil else { throw TechnicianLocationError.alreadyRequesting }
        switch manager.authorizationStatus {
        case .denied, .restricted:
            throw TechnicianLocationError.permissionDenied
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            break
        @unknown default:
            throw TechnicianLocationError.unavailable
        }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            if manager.authorizationStatus == .authorizedAlways
                || manager.authorizationStatus == .authorizedWhenInUse {
                manager.requestLocation()
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard continuation != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .denied, .restricted:
            finish(.failure(TechnicianLocationError.permissionDenied))
        case .notDetermined:
            break
        @unknown default:
            finish(.failure(TechnicianLocationError.unavailable))
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last, location.horizontalAccuracy > 0 else {
            finish(.failure(TechnicianLocationError.unavailable))
            return
        }
        finish(.success(.init(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            accuracyMeters: location.horizontalAccuracy,
            capturedAt: location.timestamp
        )))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(.failure(error))
    }

    private func finish(_ result: Result<TechnicianCapturedLocation, Error>) {
        let continuation = self.continuation
        self.continuation = nil
        continuation?.resume(with: result)
    }
}
