# Test Moon Project

Цей проект реалізує бекенд на Spring Boot та фронтенд на Three.js для візуалізації 3D сфери з інтерактивними областями, які мапуються на атлас зображень.

## Технологічний стек

- **Бекенд**: Java 21, Spring Boot 3+, Jackson (для роботи з JSON), AWT (для генерації атласу).
- **Фронтенд**: HTML5, CSS3, JavaScript (ES6+), Three.js (через CDN).

---

## Бекенд Структура

### Моделі Даних

- `UserAreaConfig`: Кореневий об'єкт для `userAreas.json`.
- `User`: Об'єкт користувача з ID та списком груп.
- `UserGroup`: Група з `groupId`, `url`, назвою тайлу (`tile`) та списком `areaIds`.
- `SphereArea`: Область на сфері з унікальним `areaId` та списком 3D вершин.
- `SphereData`: Кореневий об'єкт для `sphere.json`.

### Контролери та Ендпоінти

#### 1. UserController (`/users`)
- `GET /users`: Отримати список усіх користувачів.
- `GET /users/{userId}`: Отримати користувача за ID.
- `POST /users`: Створити нового користувача.
- `PUT /users/{userId}`: Оновити дані користувача.
- `DELETE /users/{userId}`: Видалити користувача.

#### 2. UserGroupController (`/users/{userId}/groups`)
- `GET /users/{userId}/groups`: Отримати групи користувача.
- `GET /users/{userId}/groups/{groupId}`: Отримати конкретну групу.
- `POST /users/{userId}/groups`: Додати нову групу користувачу.
- `PUT /users/{userId}/groups/{groupId}`: Оновити групу.
- `DELETE /users/{userId}/groups/{groupId}`: Видалити групу.

#### 3. SphereController (`/sphere`)
- `POST /sphere`: Генерує розметку сфери (25x25). 
  - Перші та останні 5% рядів стають цільними багатокутниками (полярні шапки).
  - Результат зберігається у `resources/sphere.json`.
- `GET /sphere`: Повертає об'єднані дані з `sphere.json` та `userAreas.json` для фронтенду.

#### 4. AtlasController (`/atlas`)
- `POST /atlas`: Формує `atlas.png` та `atlas.json`.
  - Знаходить крайні координати для кожної групи на основі `areaIds`.
  - Розтягує відповідний тайл з `resources/tiles` на отриману область в атласі.
- `GET /atlas`: Повертає файл `atlas.png`.
- `GET /atlas/info`: Повертає метадані `atlas.json`.

---

## Фронтенд (Three.js)

Знаходиться у `src/main/resources/front/index.html`.

### Функціональність:
1. **Візуалізація**: Сфера будується з окремих мешів для кожного `areaId`.
2. **Текстурування**: Використовується `atlas.png` як єдина текстура. UV-координати кожної вершини розраховуються на основі сферичних координат (phi, theta).
3. **Інтерактивність**:
   - **Hover**: При наведенні на область відображається `areaId`. Якщо область належить групі, додаються `groupId` та `url`.
   - **Click**: Якщо область має `url`, при кліку вона відкривається в новій вкладці.
4. **Управління**: Використовується `OrbitControls` для обертання та масштабування сфери.

---

## Як запустити

1. **Вимоги**: Java 21, Gradle.
2. **Збірка**:
   ```bash
   ./gradlew build
   ```
3. **Запуск**:
   ```bash
   ./gradlew bootRun
   ```
4. **Підготовка даних**:
   Після запуску необхідно ініціалізувати сферу та атлас (якщо файли ще не створені):
   - `POST http://localhost:8080/sphere`
   - `POST http://localhost:8080/atlas`
5. **Перегляд**:
   Відкрийте в браузері `http://localhost:8080/`.

---

## Конфігурація

Параметри шляхів до файлів можна змінити в `src/main/resources/application.yml`:
```yaml
app:
  user-areas-path: src/main/resources/user/userAreas.json
  sphere-path: src/main/resources/sphere.json
  atlas-png-path: src/main/resources/atlas/atlas.png
  atlas-json-path: src/main/resources/atlas/atlas.json
  tiles-dir-path: src/main/resources/tiles
```
