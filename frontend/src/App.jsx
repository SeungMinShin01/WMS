import { Route, Routes } from "react-router-dom";
import Layout from "./Layout/Layout";
import InboundPage from "./Pages/inbound/InboundPage";
import InspectionPage from "./Pages/inbound/InspectionPage";
import OutboundPage from "./Pages/outbound/OutboundPage";
import AllocationPage from "./Pages/outbound/AllocationPage";
import StockPage from "./Pages/stock/StockPage";
import HistoryPage from "./Pages/stock/HistoryPage";
import PutawayPage from "./Pages/inbound/PutawayPage";
import PickingPage from "./Pages/outbound/PickingPage";
import ProductPage from "./Pages/master/ProductPage";
import PartnerPage from "./Pages/master/PartnerPage";
import LocationPage from "./Pages/master/LocationPage";
import LoginPage from "./Pages/LoginPage";
import WorkerPage from "./Pages/admin/WorkerPage";

export default function App(props) {
  return (
    <Routes>
      {/* Layout 안의 <Outlet /> 자리에 아래 자식 화면이 들어간다 */}
      <Route path="/" element={<Layout />}>
        <Route index element={<InboundPage />} />
        <Route path="inbounds" element={<InboundPage />} />
        <Route path="inbounds/inspection" element={<InspectionPage />} />
        <Route
          path="inbounds/inspection/:documentId"
          element={<InspectionPage />}
        />
        <Route path="inbounds/putaway" element={<PutawayPage />} />
        <Route path="inbounds/putaway/:documentId" element={<PutawayPage />} />
        <Route path="outbounds" element={<OutboundPage />} />
        <Route path="outbounds/allocation" element={<AllocationPage />} />
        <Route
          path="outbounds/allocation/:documentId"
          element={<AllocationPage />}
        />
        <Route path="outbounds/picking" element={<PickingPage />} />
        <Route path="outbounds/picking/:documentId" element={<PickingPage />} />
        <Route path="stocks" element={<StockPage />} />
        <Route path="stocks/history" element={<HistoryPage />} />
        <Route path="master/products" element={<ProductPage />} />
        <Route path="master/partners" element={<PartnerPage />} />
        <Route path="master/locations" element={<LocationPage />} />
        <Route path="admin/workers" element={<WorkerPage />} />
      </Route>
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={<h2>없는 주소입니다</h2>} />
    </Routes>
  );
}
