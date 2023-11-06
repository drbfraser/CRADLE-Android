## Example usage of `NetworkStateManager`
Use it for checking if phone is on Wifi > Cell Data > SMS > None.


initiate in onCreate() or whatever init()
```kotlin
networkManager = NetworkStateManager.getInstance()
```
use the getter functions to observe LiveData, or just use it as a Boolean
```kotlin
isNetworkAvailable = networkManager.getInternetConnectivityStatus().apply {
    observe(this@LoginActivity) { netAvailable ->
        netAvailable ?: return@observe
        loginButton.isEnabled = netAvailable
        noInternetText.isVisible = !netAvailable
    }
}
```
