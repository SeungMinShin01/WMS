import { Route, Routes } from "react-router-dom";
import Layout from "./Layout/Layout";
import InboundPage from "./Pages/inbound/InboundPage";
import InspectionPage from "./Pages/inbound/InspectionPage";
import OutboundPage from "./Pages/outbound/OutboundPage";
import AllocationPage from "./Pages/outbound/AllocationPage";
import StockPage from "./Pages/stock/StockPage";
import HistoryPage from "./Pages/stock/HistoryPage";
import PutawayPage from "./Pages/inbound/PutawayPage";

// 01 /master/products  01-2 /master/partners  02 /master/locations
// 03 /inbounds  04 /inbounds/inspection
// 05 /outbounds 06 /outbounds/allocation
// 07 /stocks    08 /stocks/history
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
        <Route path="stocks" element={<StockPage />} />
        <Route path="stocks/history" element={<HistoryPage />} />
      </Route>
      <Route path="*" element={<h2>없는 주소입니다</h2>} />
    </Routes>
  );
}
