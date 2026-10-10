import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import DiscoverPage from './pages/DiscoverPage'
import PaperDetailPage from './pages/PaperDetailPage'
import LibraryPage from './pages/LibraryPage'
import UploadPage from './pages/UploadPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="/discover" element={<DiscoverPage />} />
          <Route path="/library" element={<LibraryPage />} />
          <Route path="/library/:id" element={<PaperDetailPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
