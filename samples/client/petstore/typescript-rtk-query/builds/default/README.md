## @openapitools/typescript-rtk-query-petstore@1.0.0

This generator creates a TypeScript client that utilises [Redux Toolkit Query (RTK Query)](https://redux-toolkit.js.org/rtk-query/overview).

Each OpenAPI tag is turned into a `createApi()` slice. Endpoints are typed as:

- `builder.query` — for **GET** and **HEAD** methods
- `builder.mutation` — for **POST**, **PUT**, **PATCH** and **DELETE** methods

React hooks (`useXxxQuery` / `useXxxMutation`) are exported by default.

### Installation

```bash
npm install @reduxjs/toolkit react-redux
```

### Building

```bash
npm install
npm run build
```

### Usage

```ts
import { store } from './store';
import { petApi, useGetPetByIdQuery } from './src/apis/PetApi';

// In your React component:
const { data, isLoading, error } = useGetPetByIdQuery(1);
```

### Adding to your Redux store

```ts
import { configureStore } from '@reduxjs/toolkit';
import { petApi } from './src/apis/PetApi';

export const store = configureStore({
  reducer: {
    [petApi.reducerPath]: petApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(petApi.middleware),
});
```

### Publishing

First build the package, then run:

```bash
npm publish
```

