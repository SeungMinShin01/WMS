import { Link, NavLink, Outlet } from "react-router-dom";
import logo from "../assets/logo-header.png"; // vite 기본 템플릿의 import reactLogo 와 같은 방식

// 공통 틀: 상단 헤더 + 왼쪽 메뉴 + 오른쪽 내용(<Outlet />)
export default function Layout(props) {
  return (
    <>
      <header className="top">
        {/* 로고 클릭 → 메인(입고예정). Link는 새로고침 없이 이동 */}
        <Link to="/">
          <img className="logo" src={logo} alt="first-in" />
        </Link>
        <span className="wh">WMS</span>
        <span className="sep">|</span>
        <span className="sub">창고</span>
        <span className="wh">WH-01 물류센터</span>
        <span className="right">관리자 (admin)</span>
      </header>

      <div className="body">
        <nav className="side">
          <div className="side-title">업무 메뉴</div>

          <div className="menu-group">기준정보</div>
          <NavLink to="/master/products">품목</NavLink>
          <NavLink to="/master/partners">거래처</NavLink>
          <NavLink to="/master/locations">창고·로케이션</NavLink>

          <div className="menu-group">입고관리</div>
          <NavLink to="/inbounds" end>
            입고문서
          </NavLink>
          <NavLink to="/inbounds/inspection">입고검수</NavLink>
          <NavLink to="/inbounds/putaway">물품적재</NavLink>

          <div className="menu-group">출고관리</div>
          <NavLink to="/outbounds" end>
            출고예정
          </NavLink>
          <NavLink to="/outbounds/allocation">출고지시</NavLink>

          <div className="menu-group">재고관리</div>
          <NavLink to="/stocks" end>
            재고현황
          </NavLink>
          <NavLink to="/stocks/history">입출고이력</NavLink>

          <div className="side-foot">WH-01 물류센터</div>
        </nav>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </>
  );
}
