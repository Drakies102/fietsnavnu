# Kotlin Crash Course — FietsNavNu Edition

A hands-on introduction to Kotlin using the real source code of this bike navigation app.
Each chapter explains a concept and immediately shows it in the context of code you can
find in this repository. Java equivalents are shown wherever the contrast is instructive.

**Assumed background:** solid Java knowledge.  
**Goal:** read and write idiomatic Kotlin confidently.

---

## Table of Contents

1. [Variables and Type Inference](#1-variables-and-type-inference)
2. [Functions](#2-functions)
3. [String Templates](#3-string-templates)
4. [Data Classes](#4-data-classes)
5. [Null Safety](#5-null-safety)
6. [Higher-Order Functions and Lambdas](#6-higher-order-functions-and-lambdas)
7. [Object-Oriented Kotlin](#7-object-oriented-kotlin)
8. [Scope Functions](#8-scope-functions)
9. [Coroutines — Async Without Callbacks](#9-coroutines--async-without-callbacks)
10. [Flow and StateFlow — Reactive Streams](#10-flow-and-stateflow--reactive-streams)
11. [Java Interop](#11-java-interop)

---

## 1. Variables and Type Inference

### `val` vs `var`

Kotlin has two keywords for declaring variables.

| Keyword | Equivalent | Reassignable? |
|---------|-----------|---------------|
| `val`   | `final`   | No            |
| `var`   | (none)    | Yes           |

Prefer `val` by default. Use `var` only when you genuinely need to reassign.

**Java:**
```java
final double south = 52.1;
double north = 52.4;
north = 52.5; // ok
```

**Kotlin:**
```kotlin
val south = 52.1          // compiler infers Double — no annotation needed
var north = 52.4
north = 52.5              // ok
```

### Type inference

Kotlin infers types from the right-hand side of an assignment. You can always write
the type explicitly, but you rarely need to.

```kotlin
val query = ""                     // String
val limit = 5                      // Int
val isNavigating = false           // Boolean
val pois = emptyList<Poi>()        // List<Poi>
```

You do need an explicit type when there is no initialiser:

```kotlin
private var lastBearing = 0f       // Float — suffix 'f' fixes the type
private var recalcCooldownUntil = 0L  // Long — suffix 'L'
```

Both lines are from `NavigationEngine.kt` and `MapViewModel.kt`.

### `const val`

For compile-time constants (primitives and `String`), use `const val` inside a
`companion object` or top-level `object`:

```kotlin
// NavigationEngine.kt
companion object {
    private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
}
```

Java equivalent:
```java
private static final double OFF_ROUTE_THRESHOLD_METERS = 30.0;
```

---

## 2. Functions

### Basic syntax

```kotlin
fun add(a: Int, b: Int): Int {
    return a + b
}
```

Return type comes *after* the parameter list. When the body is a single expression,
you can use the **expression body** form and drop the `return`:

```kotlin
fun add(a: Int, b: Int): Int = a + b
```

### Default parameter values

Parameters can have defaults — no more overload chains.

```kotlin
// RouteRepository.kt
suspend fun getRoute(
    waypoints: List<Pair<Double, Double>>,
    profile: String = "bike"          // default value
): RouteResult { ... }
```

Java equivalent requires an overloaded method:
```java
public RouteResult getRoute(List<Pair<Double,Double>> waypoints) {
    return getRoute(waypoints, "bike");
}
public RouteResult getRoute(List<Pair<Double,Double>> waypoints, String profile) { ... }
```

### Named arguments

When calling a function, you can name the arguments. This makes the call site
self-documenting and lets you pass them in any order.

```kotlin
// MapViewModel.kt
repository.getRoute(
    fromLat = from.lat.toDouble(),
    fromLon = from.lon.toDouble(),
    toLat   = to.lat.toDouble(),
    toLon   = to.lon.toDouble(),
    profile = profile
)
```

### Single-expression functions in Retrofit interfaces

Retrofit API interfaces with suspend functions are a perfect fit for expression bodies:

```kotlin
// GraphHopperApi.kt
interface GraphHopperApi {
    @POST("route")
    suspend fun getRoute(@Body request: GraphHopperRequest): GraphHopperResponse
}
```

The `suspend` keyword is explained in [Chapter 9](#9-coroutines--async-without-callbacks).

### Extension functions

You can add functions to existing classes without subclassing. The function receives
the object as `this`:

```kotlin
// Hypothetical extension on Double — could replace the standalone haversineMeters helpers
fun LatLng.distanceTo(other: LatLng): Double {
    val r = 6371000.0
    // ... haversine math ...
}

// then called as:
val dist = pointA.distanceTo(pointB)
```

The app currently has `haversineMeters` as private top-level functions in several
files. An extension function would consolidate them.

---

## 3. String Templates

Kotlin has built-in string interpolation. No `String.format()` needed.

```kotlin
// OverpassRepository.kt
val bbox = "$south,$west,$north,$east"

val query = "[out:json];node[rcn_ref]($bbox);out;"

// Multi-line string with triple quotes (raw string)
val routeQuery = """[out:json];relation[route=bicycle][network~"ncn|rcn|lcn"]($bbox);out geom;"""
```

For expressions (not just variables), use `${}`:

```kotlin
Log.w("MapViewModel", "POI load failed: ${it.message}")

val displayName = "Remaining: ${(remainingMeters / 1000.0).roundToInt()} km"
```

Java equivalent:
```java
String.format("POI load failed: %s", it.getMessage())
```

---

## 4. Data Classes

### The problem with Java POJOs

The Java `Poi` in `pois-service` requires 30+ lines for a 5-field value object:

```java
// pois-service/src/main/java/com/fietsrouten/pois/model/Poi.java
public class Poi {
    private long id;
    private String name;
    private String amenity;
    private double lat;
    private double lon;

    public Poi(long id, String name, String amenity, double lat, double lon) { ... }

    public long getId()        { return id; }
    public String getName()    { return name; }
    public String getAmenity() { return amenity; }
    public double getLat()     { return lat; }
    public double getLon()     { return lon; }
    // no equals(), hashCode(), toString() — you'd add those too
}
```

### Kotlin data class

```kotlin
// app/src/main/kotlin/com/fietsrouten/data/model/Models.kt
data class Poi(
    val id: Long,
    val name: String,
    val amenity: String,
    val lat: Double,
    val lon: Double
)
```

One line per field. The compiler automatically generates:
- `equals()` and `hashCode()` based on all properties
- `toString()` → `"Poi(id=1, name=Café Central, ...)"` 
- `copy()` — creates a modified copy without mutation

```kotlin
val updated = poi.copy(name = "New Name")   // all other fields unchanged
```

### Richer data classes

`RouteResult` shows that data classes can have default values too:

```kotlin
data class RouteResult(
    val coordinates: List<List<Double>>,
    val distanceMeters: Double,
    val durationMs: Long,
    val instructions: List<RouteInstruction>,
    val elevationProfile: List<Double> = emptyList()   // optional, defaults to empty
)
```

### Serialization annotations

Data classes work seamlessly with Gson via field annotations:

```kotlin
data class NominatimResult(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("display_name") val displayName: String,
    val lat: String,
    val lon: String
)
```

The JSON field `"place_id"` maps to the Kotlin property `placeId`.

### Destructuring

Data classes support destructuring declarations — unpacking multiple values at once:

```kotlin
// NavigationEngine.kt
val (nearestIndex, snappedPoint, snapDistance) = findNearestSegment(userLatLng, routeLatLngs)

// Works because SegmentResult is a data class:
private data class SegmentResult(val index: Int, val snappedPoint: LatLng, val distanceMeters: Double)
```

Pairs also destructure:

```kotlin
// RouteRepository.kt — destructuring a Pair<Double, Double>
points = waypoints.map { (lat, lon) -> listOf(lon, lat) }
```

---

## 5. Null Safety

This is one of Kotlin's most important features. The type system distinguishes
between nullable and non-nullable references at compile time, eliminating most
`NullPointerException`s.

### Nullable types

A type is non-nullable by default. To allow `null`, append `?`:

```kotlin
val name: String    = "Café"    // can never be null
val name: String?   = null      // ok

var nextInstruction: RouteInstruction?   // from NavigationSession.kt — may be null
```

The compiler prevents you from calling methods on a nullable without a null check.

### Safe call operator `?.`

Calls the method/property only if the value is non-null; otherwise returns `null`.

```kotlin
// MapViewModel.kt
val ref = el.tags?.get("rcn_ref")   // returns null if tags is null
```

Chain multiple safe calls:

```kotlin
val speedKmh = location?.speed?.times(3.6)
```

### Elvis operator `?:`

Provides a default value when the left side is null:

```kotlin
// OverpassRepository.kt
val name = el.tags?.get("name") ?: el.tags?.get("ref") ?: return@mapNotNull null
val network = el.tags?.get("network") ?: "lcn"
```

Read `?:` as "if null, use…". It replaces:
```java
String name = tags.get("name") != null ? tags.get("name") : tags.get("ref");
```

### Non-null assertion `!!`

Forces a nullable to non-null; throws `NullPointerException` if it is null.
Use sparingly — it opts out of null safety.

```kotlin
val path = response.paths.first()   // throws NoSuchElementException if empty — ok here
```

### `let` for null-conditional blocks

```kotlin
// MapViewModel.kt
_userLocation.value?.let { (lat, lon) ->
    NominatimResult(placeId = -1L, displayName = "Mijn locatie",
                    lat = lat.toString(), lon = lon.toString())
}
// Returns null if _userLocation.value is null; otherwise executes the block
```

### `?.takeIf`

Returns the value if a predicate holds, otherwise null — great for filtering in chains:

```kotlin
// OverpassRepository.kt
member.geometry
    ?.takeIf { it.size >= 2 }       // null if fewer than 2 points
    ?.map { pt -> listOf(pt.lon, pt.lat) }
```

### `lateinit var`

For non-nullable properties that cannot be initialised in the constructor (common with
Android view binding):

```kotlin
// MainActivity.kt
private lateinit var binding: ActivityMainBinding

override fun onCreate(...) {
    binding = ActivityMainBinding.inflate(layoutInflater)  // initialised here
}
```

Accessing `binding` before assignment throws `UninitializedPropertyAccessException`.

---

## 6. Higher-Order Functions and Lambdas

Functions are first-class citizens in Kotlin. You can pass them as arguments,
return them, and store them in variables.

### Lambda syntax

```kotlin
// Full form
val double: (Int) -> Int = { x: Int -> x * 2 }

// Type inferred from context
val double = { x: Int -> x * 2 }

// Single parameter — use implicit 'it'
pois.filter { it.amenity == "cafe" }
```

### Collection operations

The app uses these extensively. All are higher-order functions that accept lambdas.

**`map`** — transform each element:
```kotlin
// PoiServiceRepository.kt
api.getPois(south, west, north, east).features.map { f ->
    Poi(
        id      = f.properties.id,
        name    = f.properties.name,
        amenity = f.properties.amenity,
        lat     = f.geometry.coordinates[1],
        lon     = f.geometry.coordinates[0]
    )
}
```

**`filter`** — keep elements matching a predicate:
```kotlin
// OverpassRepository.kt
return nodes.filter { node ->
    sample.any { pt ->
        abs(node.lat - pt[1]) < 0.001 && abs(node.lon - pt[0]) < 0.0015
    }
}
```

**`any`** — true if at least one element matches:
```kotlin
// PoiServiceRepository.kt
sample.any { pt -> haversineMeters(poi.lat, poi.lon, pt[1], pt[0]) <= 150.0 }
```

**`mapNotNull`** — map + drop nulls in one step:
```kotlin
// OverpassRepository.kt
api.query(query).elements.mapNotNull { el ->
    if (el.type == "node" && el.lat != null && el.lon != null) {
        val ref = el.tags?.get("rcn_ref") ?: return@mapNotNull null
        Knooppunt(el.id, el.lat, el.lon, ref)
    } else null
}
```

**`filterIndexed`** — filter with access to the index:
```kotlin
// OverpassRepository.kt — sample every 5th coordinate
val sample = routeCoords.filterIndexed { i, _ -> i % 5 == 0 }
```

**`zipWithNext`** — pairs each element with the one after it:
```kotlin
// MapViewModel.kt — distance of each leg between knooppunten
fun getLegDistances(nodes: List<Knooppunt>): List<Double> =
    nodes.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }
```

**`minOf` / `maxOf`** on a property:
```kotlin
// MapViewModel.kt — bounding box of a route
val south = coords.minOf { it[1] }
val north = coords.maxOf { it[1] }
```

**`toMap`** — build a map from a list of pairs:
```kotlin
// MapViewModel.kt
.map { (profile, deferred) -> profile to async { runCatching { request(profile.apiName) } } }
.mapNotNull { (profile, deferred) -> deferred.await().getOrNull()?.let { profile to it } }
.toMap()
```

### Trailing lambda syntax

When the last argument to a function is a lambda, you can move it outside the parentheses:

```kotlin
// These are identical:
nodes.filter({ node -> node.lat > 52.0 })
nodes.filter { node -> node.lat > 52.0 }   // trailing lambda — preferred
```

When the only argument is a lambda, parentheses are dropped entirely:
```kotlin
viewModelScope.launch {
    // coroutine body
}
```

### `return@label`

Inside a lambda, `return` is not allowed (it would return from the enclosing function).
Use a labelled return to exit only the lambda:

```kotlin
// OverpassRepository.kt
elements.mapNotNull { el ->
    if (el.type != "relation") return@mapNotNull null    // exits this lambda iteration
    // ...
}
```

---

## 7. Object-Oriented Kotlin

### Classes

Kotlin classes are `public` and `final` by default (unlike Java where `public` is
explicit and all classes are open to subclassing).

```kotlin
class RouteRepository {
    // primary constructor parameters become properties if marked val/var
}
```

### Primary constructor

```kotlin
// The constructor lives in the class header
class NavigationEngine {
    private var lastBearing = 0f
    // ...
}

// With constructor parameters that become properties:
data class Knooppunt(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val ref: String
)
```

### `companion object` — static members

Kotlin has no `static` keyword. Use a `companion object` inside the class:

```kotlin
// NavigationEngine.kt
class NavigationEngine {
    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
    }
    // ...
}
```

Java:
```java
class NavigationEngine {
    private static final double OFF_ROUTE_THRESHOLD_METERS = 30.0;
}
```

### `object` — singletons

A top-level `object` declaration is a singleton — one instance, created lazily on first
access. Perfect for application-wide configuration:

```kotlin
// Config.kt
object Config {
    const val GRAPHHOPPER_BASE_URL = "https://fietsnav-routing.learndelingo.nl"
    const val NOMINATIM_BASE_URL   = "https://fietsnav-geocoding.learndelingo.nl"
    const val OVERPASS_BASE_URL    = "https://overpass-api.de/"
    const val POI_BASE_URL         = "https://fietsnav-pois.learndelingo.nl"
    const val MAP_STYLE_URL        = "https://tiles.openfreemap.org/styles/bright"
    const val MAP_STYLE_DARK_URL   = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
}
```

Accessed as `Config.GRAPHHOPPER_BASE_URL` — looks like a static field, no
instantiation needed.

### `enum class`

```kotlin
// MapViewModel.kt
enum class PlannerMode { ADDRESS, KNOOPPUNTEN }

enum class CyclingProfile(val apiName: String) {
    BIKE("bike"), MTB("mtb"), RACING("racingbike")
}
```

Enums can hold properties and methods. `CyclingProfile.BIKE.apiName` returns `"bike"`.

`CyclingProfile.values()` returns all values as an array — used to fetch routes for all
profiles in parallel:

```kotlin
CyclingProfile.values().map { profile ->
    profile to async { runCatching { request(profile.apiName) } }
}
```

### `interface` with default parameters

Retrofit API interfaces look identical to Java interfaces but support Kotlin default
parameter values:

```kotlin
// NominatimApi.kt
interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",   // default — callers can omit
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "nl"
    ): List<NominatimResult>
}
```

### `open` and inheritance

To allow a class to be subclassed (or a function to be overridden), mark it `open`:

```kotlin
open class BaseRepository {
    open fun buildClient(): OkHttpClient = OkHttpClient()
}

class OverpassRepository : BaseRepository() {
    override fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { ... }
        .build()
}
```

The app doesn't use inheritance heavily — composition via constructor injection is
preferred.

### `when` expression

`when` replaces both `switch` and long `if/else if` chains, and it is an *expression*
— it returns a value.

```kotlin
// How you could interpret GraphHopper instruction signs:
val icon = when (instruction.sign) {
    0    -> R.drawable.ic_straight
    -2   -> R.drawable.ic_turn_left
     2   -> R.drawable.ic_turn_right
    -3   -> R.drawable.ic_sharp_left
     3   -> R.drawable.ic_sharp_right
    else -> R.drawable.ic_straight
}
```

`when` also works with type checks (`is`) and ranges (`in 0..10`).

---

## 8. Scope Functions

Scope functions let you run a block of code *in the context of* an object. They avoid
repeating a variable name and make initialization or transformation chains more readable.

| Function | Context object | Returns       | Typical use |
|----------|---------------|---------------|-------------|
| `let`    | `it`          | lambda result | null-safe transform |
| `apply`  | `this`        | the object    | builder / initialization |
| `run`    | `this`        | lambda result | transform with `this` |
| `also`   | `it`          | the object    | side effects in a chain |
| `with`   | `this`        | lambda result | grouping calls on an object |

### `let`

Already seen for null safety. Also used to transform and immediately use a value:

```kotlin
// MapViewModel.kt
_userLocation.value?.let { (lat, lon) ->
    NominatimResult(placeId = -1L, displayName = "Mijn locatie",
                    lat = lat.toString(), lon = lon.toString())
}
```

### `apply` — builder pattern

```kotlin
// OverpassRepository.kt uses the builder pattern directly; with apply it reads:
val client = OkHttpClient.Builder().apply {
    addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("Accept", "*/*")
                .header("User-Agent", "FietsNavNu/1.0 Android")
                .build()
        )
    }
}.build()
```

`apply` returns the receiver (`OkHttpClient.Builder`), so you can chain `.build()`.

### `runCatching` — functional error handling

`runCatching` wraps a block in a `try/catch` and returns a `Result<T>`. It is not one
of the five scope functions but uses the same idea:

```kotlin
// MapViewModel.kt
runCatching {
    poiRepository.getPoisInBbox(south, west, north, east)
}.onSuccess { all ->
    _pois.value = poiRepository.filterNearRoute(all, coords)
}.onFailure {
    Log.w("MapViewModel", "POI load failed: ${it.message}")
    _poisVisible.value = false
}
```

This replaces the try/catch/finally pattern and is composable. `getOrNull()` and
`getOrDefault()` extract the value:

```kotlin
// MapViewModel.kt
emit(runCatching { repository.searchAddress(query) }.getOrDefault(emptyList()))
```

---

## 9. Coroutines — Async Without Callbacks

Coroutines are Kotlin's approach to asynchronous programming. They replace callbacks,
`AsyncTask`, `RxJava`, and thread pools for most Android use cases.

### The core idea

A **suspending function** can pause without blocking a thread, then resume when its
result is ready. The `suspend` keyword marks it:

```kotlin
// RouteRepository.kt
suspend fun searchAddress(query: String): List<NominatimResult> =
    nominatimApi.search(query)   // pauses here while the network request runs
```

From the caller's perspective, this looks synchronous — no callback, no `.then()`.

### `suspend` all the way up

A `suspend` function can only be called from another `suspend` function or a
**coroutine builder** (`launch`, `async`).

```kotlin
// MapViewModel.kt — starting a coroutine from a ViewModel
fun calculateRoute() {
    viewModelScope.launch {          // coroutine builder
        _isLoading.value = true
        val results = fetchAllProfiles { profile ->   // suspend call — ok inside launch
            repository.getRoute(...)
        }
        _isLoading.value = false
    }
}
```

`viewModelScope` is a `CoroutineScope` tied to the ViewModel lifecycle — coroutines
launched here are cancelled automatically when the ViewModel is cleared.

### `async` and `await` — parallel work

`async` starts a coroutine that returns a value (a `Deferred<T>`). Call `.await()` to
get the result:

```kotlin
// MapViewModel.kt — fetch all three cycling profiles simultaneously
private suspend fun fetchAllProfiles(
    request: suspend (profile: String) -> RouteResult
): Map<CyclingProfile, RouteResult> = coroutineScope {
    CyclingProfile.values()
        .map { profile ->
            profile to async { runCatching { request(profile.apiName) } }
        }
        .mapNotNull { (profile, deferred) ->
            deferred.await().getOrNull()?.let { profile to it }
        }
        .toMap()
}
```

`coroutineScope { }` creates a scope that waits for all children before returning.
The three `async` blocks run concurrently — the total time is the slowest of the three,
not the sum.

### Structured concurrency

Kotlin enforces **structured concurrency**: child coroutines live inside their parent's
scope. If the parent is cancelled (e.g. ViewModel cleared), all children are cancelled
too — no leaks.

```
viewModelScope
  └── launch { calculateRoute }
        └── coroutineScope { fetchAllProfiles }
              ├── async { bike route }
              ├── async { mtb route }
              └── async { racing route }
```

### Calling suspend functions from non-suspend context

Outside a coroutine, use a builder. The builders differ in lifecycle:

| Builder | Scope | Use |
|---------|-------|-----|
| `viewModelScope.launch` | ViewModel lifecycle | Android ViewModel |
| `lifecycleScope.launch` | Fragment/Activity lifecycle | Android UI |
| `GlobalScope.launch` | App lifetime | Avoid — leaks |
| `runBlocking` | Blocks the calling thread | Tests only |

### Retrofit + coroutines

Adding `suspend` to a Retrofit interface method is all you need — Retrofit handles
the thread switching automatically:

```kotlin
// GraphHopperApi.kt
interface GraphHopperApi {
    @POST("route")
    suspend fun getRoute(@Body request: GraphHopperRequest): GraphHopperResponse
}
```

No `Call<T>`, no `enqueue`, no callback.

---

## 10. Flow and StateFlow — Reactive Streams

Coroutines handle a single async value. **Flow** handles a stream of values over time.
`StateFlow` is a special Flow that always holds the latest value — think of it as a
reactive variable.

### `StateFlow` as UI state

```kotlin
// MapViewModel.kt — every piece of UI state is a StateFlow
private val _isLoading = MutableStateFlow(false)
val isLoading: StateFlow<Boolean> = _isLoading   // read-only view exposed to UI
```

The pattern:
- `MutableStateFlow` — internal, writable.
- `StateFlow` — public, read-only. The UI observes this.

The underscore prefix (`_isLoading`) is the conventional name for the mutable backing field.

Setting a value:
```kotlin
_isLoading.value = true
```

Reading in a coroutine:
```kotlin
viewModelScope.launch {
    isLoading.collect { loading ->
        // called every time the value changes
    }
}
```

### Building a search with `debounce` and `flatMapLatest`

```kotlin
// MapViewModel.kt — address search with 300 ms debounce
private val fromQuery = MutableStateFlow("")

init {
    viewModelScope.launch {
        fromQuery
            .debounce(300)           // wait 300 ms of silence before acting
            .flatMapLatest { query ->
                flow {
                    if (query.length >= 3)
                        emit(runCatching { repository.searchAddress(query) }
                            .getOrDefault(emptyList()))
                    else
                        emit(emptyList())
                }
            }
            .collect { _fromSuggestions.value = it }
    }
}

fun searchFrom(query: String) { fromQuery.value = query }
```

Step by step:
1. `fromQuery` emits every keystroke.
2. `debounce(300)` drops emissions that arrive within 300 ms of each other — the
   network call only fires after the user pauses typing.
3. `flatMapLatest` starts a new inner flow for each query and *cancels the previous one*
   if a new query arrives — so stale results never overwrite fresh ones.
4. `collect` receives the final list and writes it to `_fromSuggestions`.

### `flow { }` builder

`flow { }` creates a cold stream — it only runs when collected:

```kotlin
flow {
    emit(repository.searchAddress(query))   // produces one value
}
```

Cold means each collector gets its own independent execution. Contrast with `StateFlow`,
which is hot — it runs regardless of collectors and replays the latest value to new ones.

### Summary of reactive types

| Type | Values | Hot/Cold | Use |
|------|--------|----------|-----|
| `Flow<T>` | Many | Cold | Streams of data |
| `StateFlow<T>` | Many (holds latest) | Hot | UI state |
| `MutableStateFlow<T>` | Many (writable) | Hot | Internal mutable state |
| `Deferred<T>` | One (future) | Hot | `async` result |

---

## 11. Java Interop

Kotlin runs on the JVM and interoperates with Java seamlessly. The app uses this in
two directions.

### Calling Java from Kotlin

The `pois-service` backend is Java. Its `Poi` class can be used from Kotlin without any
changes:

```kotlin
// Kotlin can call Java classes directly
val poi = Poi(1L, "Café Central", "cafe", 52.37, 4.89)
val name = poi.name   // Kotlin calls the Java getter transparently
```

Kotlin maps Java getters (`getName()`) to properties (`name`) automatically.

### Calling Kotlin from Java

Java can call Kotlin classes equally transparently. The Spring Boot `@SpringBootApplication`
entry point in Java can reference Kotlin classes in the same project.

### `@JvmStatic` and `@JvmField`

When Java code needs to call a `companion object` member, annotate it so Java sees it as
a true static:

```kotlin
companion object {
    @JvmStatic
    fun create(): NavigationEngine = NavigationEngine()

    @JvmField
    val DEFAULT_THRESHOLD = 30.0
}
```

Without `@JvmStatic`, Java would call `NavigationEngine.Companion.create()`.

### Nullability annotations

Kotlin's `?` types map to `@Nullable`/`@NonNull` at the bytecode level. When calling
Java code that has no annotations, Kotlin treats the return type as a **platform type**
(`String!`) — you are responsible for null checks.

```kotlin
// Android's Location.getBearing() returns float — no nullability info
// Kotlin exposes it as a platform type; check with hasBearing() first
if (location.hasBearing() && location.speed > 0.5f) {
    lastBearing = location.bearing
}
```

### Converting the `pois-service` to Kotlin

The three Java files convert directly. Here is `Poi.java` → Kotlin:

**Before (Java):**
```java
public class Poi {
    private long id;
    private String name;
    private String amenity;
    private double lat;
    private double lon;

    public Poi(long id, String name, String amenity, double lat, double lon) {
        this.id = id; this.name = name; this.amenity = amenity;
        this.lat = lat; this.lon = lon;
    }

    public long getId()        { return id; }
    public String getName()    { return name; }
    public String getAmenity() { return amenity; }
    public double getLat()     { return lat; }
    public double getLon()     { return lon; }
}
```

**After (Kotlin):**
```kotlin
data class Poi(
    val id: Long,
    val name: String,
    val amenity: String,
    val lat: Double,
    val lon: Double
)
```

`PoiController.java` → Kotlin (Spring annotations work identically):

```kotlin
@RestController
@RequestMapping("/api/pois")
class PoiController(private val repository: PoiRepository) {

    @GetMapping
    fun getPois(
        @RequestParam south: Double,
        @RequestParam west: Double,
        @RequestParam north: Double,
        @RequestParam east: Double,
        @RequestParam(defaultValue = "cafe,restaurant,fast_food") types: String
    ): ResponseEntity<Map<String, Any>> {
        val typeList = types.split(",")
        val pois = repository.findInBbox(south, west, north, east, typeList)

        val features = pois.map { poi ->
            mapOf(
                "type" to "Feature",
                "geometry" to mapOf(
                    "type" to "Point",
                    "coordinates" to doubleArrayOf(poi.lon, poi.lat)
                ),
                "properties" to mapOf(
                    "id" to poi.id,
                    "name" to poi.name,
                    "amenity" to poi.amenity
                )
            )
        }

        return ResponseEntity.ok(mapOf("type" to "FeatureCollection", "features" to features))
    }
}
```

The constructor injection moves into the class header (`class PoiController(private val repository: PoiRepository)`),
and Java's `Map.of()` becomes the Kotlin `mapOf()` function.

---

## Quick Reference

```kotlin
// Variables
val x = 42                          // immutable
var y = "hello"                     // mutable
const val PI = 3.14                 // compile-time constant

// Null safety
val a: String? = null
val len = a?.length ?: 0            // safe call + elvis
val upper = a!!.uppercase()         // non-null assertion (throws if null)

// Data class
data class Point(val x: Double, val y: Double)
val p = Point(1.0, 2.0)
val q = p.copy(y = 5.0)

// Lambdas
val doubled = listOf(1, 2, 3).map { it * 2 }
val evens   = listOf(1, 2, 3).filter { it % 2 == 0 }

// Coroutine
viewModelScope.launch {
    val result = suspendFun()       // awaited without blocking
}

// StateFlow
private val _count = MutableStateFlow(0)
val count: StateFlow<Int> = _count
_count.value = 1

// when
val label = when (code) {
    200  -> "OK"
    404  -> "Not found"
    else -> "Unknown"
}

// Scope functions
val obj = MyClass().apply { name = "x"; value = 1 }
val len = someString?.let { it.length } ?: 0
```

---

*All examples are from `app/app/src/main/kotlin/` and `pois-service/src/main/java/` in this repository.*
