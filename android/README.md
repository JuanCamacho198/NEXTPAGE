# NextPage 📖

**NextPage** es una app de lectura Android moderna construida con **Kotlin + Jetpack Compose + Material 3**. 
Soporta **EPUB** y **PDF** con una experiencia de lectura customizable, sincronización con Supabase y modo local offline.

## ✨ Features

### 📚 Lector
- **EPUB**: Renderizado de capítulos con WebView, navegación entre capítulos, soporte de CSS
- **PDF**: Renderizado página por página con soporte de zoom (tap zones)
- **Ajustes de lectura**: Tamaño de fuente, interlineado, tema (Dark/Sepia/Light)
- **Sleep Timer**: Temporizador de lectura con presets de 5/10/15/30 min + modo **"Fin de capítulo"**
- **Bookmarks + Highlights**: Creá marcadores y resaltados mientras leés

### 🏠 Home
- Resumen diario (minutos, sesiones, progreso)
- "Continue reading" con libro en curso
- Estantería horizontal con libros recientes
- Accesos rápidos: importar libro, resaltados, ajustes
- **Notificaciones**: Campana con bottom sheet de notificaciones mock

### 📖 Estantería (Library)
- Grid 2 columnas con covers reales (Coil)
- Tabs: Todos / Leyendo / Pendientes / Completados
- Sort: fecha agregada, título, autor, última lectura
- Importar EPUB/PDF desde archivo

### 🔖 Resaltados
- Tabs con iconos por color (Citas / Ideas / Pasajes)
- Stats widgets con contadores
- Búsqueda y filtro

### ⚙️ Ajustes
- Sección de Cuenta con datos del usuario
- Preferencias: Tema, Tamaño de fuente, Sincronización, Acerca de
- Notificaciones integradas

### ☁️ Sincronización
- Auth con Supabase (Google OAuth + Email/Password)
- Sincronización de progreso, highlights y bookmarks
- Modo local offline sin configuración de Supabase

## 🏗️ Stack

| Capa | Tecnología |
|------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **Arquitectura** | Clean Architecture (presentation → domain → data) |
| **Navegación** | Jetpack Navigation Compose + BottomNavigation |
| **Persistencia Local** | Room (SQLite) |
| **Backend** | Supabase (Auth, PostgREST, Storage) |
| **EPUB** | Custom parser con ZipInputStream + WebView |
| **PDF** | PdfRenderer API de Android |
| **Imágenes** | Coil (Compose integration) |
| **DI** | Manual (AppContainer) |
| **Testing** | JUnit + MockK + Coroutines Test |
| **Lint** | Detekt |

## 🚀 Build & Run

### Prerequisitos
- Android Studio Hedgehog o más reciente
- JDK 17+
- Gradle 8.9

### Comandos

```powershell
# Build debug APK
.\gradlew.bat assembleDebug

# Build release APK
.\gradlew.bat assembleRelease

# Run unit tests
.\gradlew.bat testDebugUnitTest

# Run all tests
.\gradlew.bat test

# Run a specific test class
.\gradlew.bat testDebugUnitTest --tests "com.nextpage.presentation.viewmodel.AuthViewModelTest"

# Static analysis
.\gradlew.bat detekt

# Verify no hardcoded strings in AuthScreen
.\gradlew.bat verifyAuthScreenNoHardcodedStrings
```

### APK pre-built

El APK debug más reciente está en `.apks/nextpage-latest.apk`.

### Configuración de Supabase (opcional)

Si querés sincronización con Supabase, agregá las keys en `local.properties`:

```
supabase.url=https://your-project.supabase.co
supabase.anonkey=your-anon-key
supabase.auth.redirect.scheme=nextpage
supabase.auth.redirect.host=auth
supabase.auth.redirect.path=/callback
supabase.storage.books.bucket=books
```

Sin estas keys, la app funciona en **modo local offline** sin problemas.

## 🧪 Tests

| Suite | Tests |
|-------|-------|
| ViewModel — Auth | 20+ |
| ViewModel — Home | 10+ |
| ViewModel — Library | 10+ |
| ViewModel — Reader (Progress) | 10+ |
| ViewModel — Reader (Sleep Timer) | 10 |
| Repository | 10+ |
| Domain — Use Cases | 5+ |
| UI — Screens | 5+ |

**Total: ~80 tests unitarios**

## 📁 Estructura del Proyecto

```
android/
├── app/
│   └── src/
│       ├── main/java/com/nextpage/
│       │   ├── data/           # Data layer (Room, Supabase, EPUB, PDF)
│       │   ├── domain/         # Domain layer (models, repos, usecases)
│       │   ├── presentation/   # UI layer (screens, viewmodels, theme)
│       │   ├── ui/             # Reusable components (atoms, molecules)
│       │   ├── di/             # Dependency injection
│       │   └── MainActivity.kt
│       ├── test/               # Unit tests (JVM)
│       └── androidTest/        # Instrumented tests
├── .apks/                      # Pre-built APKs
├── config/detekt/              # Detekt configuration
├── design/                     # Design tokens (Pencil file)
├── openspec/                   # SDD artifacts (specs, proposals)
└── build.gradle.kts
```

## 🧭 Navegación

- **Auth** (no autenticado) → tras login → **Home**
- **Bottom Nav** (autenticado): Home | Estantería | Resaltados | Ajustes
- **Reader** se abre al seleccionar un libro desde Home o Estantería

## 📐 SDD (Spec-Driven Development)

Este proyecto usa un flujo SDD documentado en `openspec/`. Cada cambio mayor pasa por:
1. **Proposal** → 2. **Specs** → 3. **Design** → 4. **Tasks** → 5. **Apply** → 6. **Verify** → 7. **Archive**

La memoria de decisiones se persiste en **Engram** (proyecto `nextpage`).

## 📄 Licencia

Uso interno — NextPage
