#!/usr/bin/env python3
# 시연 시드 생성기 (scripts/seed/gen.py)
# 실행: python scripts/seed/gen.py wms-core/src/main/resources/db/seed/R__seed_demo.sql
# 시연 시드 생성기: 날짜순으로 입고·출고를 흉내 내어 재고·할당·피킹·폐기 숫자가 서로 맞게 만든다.
import random
from datetime import date, datetime, timedelta
from collections import defaultdict

random.seed(20261014)
NOW = datetime(2026, 10, 14, 10, 0)
TODAY = NOW.date()
START = date(2026, 8, 17)
PW = "$2a$10$dq5YRcvVNd9stYj70NQr0eP7RPrG3lABnP3VSbAquVBboDqiSPwdC"  # 작업자 공통 (V6 과 같은 해시)
ADMIN_PW = "$2a$10$tWwoW1y915NmzgHPRt7krO9hGpEhxTbqBj5LsG1gTj/dH2dGoYAlK"  # 관리자 전용 (BCrypt)

def q(v):
    if v is None: return "NULL"
    if isinstance(v, bool): return "TRUE" if v else "FALSE"
    if isinstance(v, (int,)): return str(v)
    if isinstance(v, datetime): return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, date): return "'" + v.strftime("%Y-%m-%d") + "'"
    return "'" + str(v).replace("'", "''") + "'"

# ---------------- 화주 · 품목 · 거래처 ----------------
TENANTS = [(1, "HLT", "(주)새봄헬스", "A"), (2, "BEV", "(주)맑은샘음료", "B"), (3, "FOD", "(주)들녘푸드", "C")]
ZONE_OF = {t[0]: t[3] for t in TENANTS}
CODE_OF = {t[0]: t[1] for t in TENANTS}

# (이름, 규격, 출고허용잔여일, 소비기한일수)
P = {
 1: [("종합비타민 데일리", "60정*12", 90, 730), ("비타민C 1000", "120정*12", 90, 730), ("오메가3 알티지", "60캡슐*10", 90, 540),
     ("유산균 프로바이오", "2g*50포*10", 60, 365), ("홍삼정 스틱", "10ml*30포*6", 90, 720), ("루테인 아이케어", "30캡슐*20", 90, 600),
     ("콜라겐 젤리", "20g*14포*8", 30, 240), ("마그네슘 플러스", "90정*12", 90, 730), ("밀크씨슬", "60정*12", 90, 720),
     ("단백질 쉐이크 초코", "40g*14*6", 30, 300), ("철분 액상", "20ml*10*10", 30, 180), ("어린이 젤리 비타", "4g*60*8", 30, 210)],
 2: [("생수 500ml", "500ml*20", 60, 540), ("탄산수 레몬", "350ml*24", 60, 360), ("보리차", "500ml*20", 60, 360),
     ("캔커피 라떼", "240ml*30", 30, 270), ("오렌지 주스", "1L*12", 21, 120), ("착즙 사과주스", "200ml*24", 14, 75),
     ("두유 오리지널", "190ml*24", 30, 180), ("멸균우유", "1L*10", 21, 120), ("이온음료", "620ml*20", 60, 360),
     ("녹차 PET", "500ml*20", 60, 360), ("에너지 드링크", "250ml*30", 30, 300), ("콤부차 복숭아", "330ml*24", 14, 90)],
 3: [("매운 라면", "120g*40", 30, 180), ("짜장 라면", "140g*32", 30, 180), ("컵라면 소", "65g*30", 30, 180),
     ("즉석 백미밥", "210g*24", 30, 270), ("참치캔 살코기", "150g*24", 60, 1080), ("토마토 파스타소스", "600g*12", 60, 540),
     ("초코 파이", "39g*12*8", 30, 210), ("감자칩 오리지널", "60g*20", 30, 180), ("크래커", "280g*12", 30, 270),
     ("생면 우동 (상온)", "210g*20", 10, 60), ("카스테라 미니", "40g*30", 7, 45), ("햄 통조림", "200g*24", 60, 1080)],
}
products = []   # dict
pid = 0
for t in (1, 2, 3):
    for i, (nm, spec, msd, shelf) in enumerate(P[t]):
        pid += 1
        products.append(dict(id=pid, tenant=t, code=f"SKU-{t}{i+1:03d}", name=nm, spec=spec, unit="BOX",
                             min_ship=msd, shelf=shelf, active=True))
products[11]["active"] = False   # 어린이 젤리 비타: 판매 종료
products[35 - 0]["active"] = True
prod_by_t = defaultdict(list)
for p in products: prod_by_t[p["tenant"]].append(p)

SUP = {1: [("온누리헬스케어", "서울 금천구 가산디지털1로 5"), ("바른원료", "경기 화성시 향남읍 제약공단2길 7"), ("자연담은", "충북 오송읍 오송생명1로 30")],
       2: [("맑은샘 이천공장", "경기 이천시 부발읍 경충대로 101"), ("푸른들음료", "충북 음성군 대소면 삼양로 66"), ("한결F&B", "경북 김천시 혁신1로 12")],
       3: [("한빛식품", "경기 안성시 공단로 12"), ("다온제과", "경기 평택시 산단대로 88"), ("오곡농산", "전북 익산시 왕궁면 국가식품로 21")]}
CUS = {1: [("건강한약국 체인", "서울 강남구 테헤란로 211"), ("헬스몰 온라인", "경기 성남시 분당구 판교역로 166"), ("웰니스마트", "부산 해운대구 센텀중앙로 79")],
       2: [("편의점 GS 물류", "경기 용인시 처인구 백암면 근곡로 9"), ("하나로마트 유통", "경기 고양시 덕양구 통일로 140"), ("카페온 본사", "서울 마포구 양화로 45")],
       3: [("이마트 물류센터", "경기 여주시 가남읍 여주남로 72"), ("동네마트 연합", "인천 남동구 남동대로 215"), ("학교급식 조합", "대전 유성구 대학로 99")]}
partners = []
ptid = 0
for t in (1, 2, 3):
    n = 0
    for kind, rows in (("SUPPLIER", SUP[t]), ("CUSTOMER", CUS[t])):
        for nm, addr in rows:
            ptid += 1; n += 1
            partners.append(dict(id=ptid, tenant=t, code=f"PT-{n:03d}", name=nm, type=kind,
                                 contact=f"0{random.choice([2,31,32,41,51])}-{random.randint(200,899)}-{random.randint(1000,9999)}",
                                 addr=addr, active=True))
partners[-1]["active"] = False  # 학교급식 조합: 거래 중지
sup_by_t = {t: [p for p in partners if p["tenant"] == t and p["type"] == "SUPPLIER"] for t in (1, 2, 3)}
cus_by_t = {t: [p for p in partners if p["tenant"] == t and p["type"] == "CUSTOMER" and p["active"]] for t in (1, 2, 3)}

# ---------------- 칸 120 (A·B·C × 통로 4 × 베이 10) ----------------
locations = []
lid = 0
CAP = {"A": 150, "B": 240, "C": 200}
for z in "ABC":
    for a in range(1, 5):
        for b in range(1, 11):
            lid += 1
            locations.append(dict(id=lid, code=f"{z}-{a:02d}-{b:02d}", zone=z, cap=CAP[z], active=True, remark=None))
def loc(code): return next(l for l in locations if l["code"] == code)
for c in ("B-04-10", "C-04-10"):
    loc(c)["cap"] = None; loc(c)["remark"] = "바닥 적치 구역 (용량 제한 없음)"
for c, r in (("A-03-07", "랙 기둥 파손, 수리 중"), ("B-02-05", "스프링클러 점검으로 사용 중지"), ("C-01-10", "통로 확장 공사 예정")):
    loc(c)["active"] = False; loc(c)["remark"] = r
loc("A-01-01")["remark"] = "입고 검수대 옆, 소량 긴급 출고용"
loc("C-02-01")["remark"] = "통조림 전용 권장"

# ---------------- 계정 ----------------
users = [dict(id=1, login="admin", name="창고관리자", role="ADMIN"),
         dict(id=2, login="worker01", name="김도윤", role="WORKER"),
         dict(id=3, login="worker02", name="이서연", role="WORKER"),
         dict(id=4, login="worker03", name="박준호", role="WORKER")]

# ---------------- 상태 ----------------
lots, docs, items, details, stocks, disposals, plists, pitems = [], [], [], [], [], [], [], []
stock_key = {}       # (lot_id, loc_id) -> stock dict
lot_key = {}         # (product_id, lot_code) -> lot
docno_seq = defaultdict(int)
lot_seq = defaultdict(int)

def doc_no(t, typ, d):
    k = (t, typ, d); docno_seq[k] += 1
    return f"{CODE_OF[t]}-{typ}-{d:%Y%m%d}-{docno_seq[k]:03d}"

def loc_used(l):  return sum(s["qty"] for s in stocks if s["loc"] == l["id"])
def loc_tenant(l):
    for s in stocks:
        if s["loc"] == l["id"] and s["qty"] > 0: return s["tenant"]
    return None

def find_location(t, lot_id, qty):
    zone = ZONE_OF[t]
    cands = [l for l in locations if l["zone"] == zone and l["active"]]
    # 같은 LOT 칸 → 잔량 칸 → 빈 칸 (적치 추천과 같은 우선순위 흉내)
    same = [l for l in cands if (lot_id, l["id"]) in stock_key and stock_key[(lot_id, l["id"])]["qty"] > 0]
    used = [l for l in cands if loc_used(l) > 0 and l not in same]
    empty = [l for l in cands if loc_used(l) == 0]
    random.shuffle(used); random.shuffle(empty)
    order = same + (empty + used if random.random() < 0.7 else used + empty)
    for l in order:
        if l["cap"] is not None and loc_used(l) + qty > l["cap"]: continue
        lt = loc_tenant(l)
        if lt is not None and lt != t: continue
        return l
    return None

def add_stock(t, lot_id, l, qty, when):
    k = (lot_id, l["id"])
    if k not in stock_key:
        s = dict(id=len(stocks) + 1, tenant=t, lot=lot_id, loc=l["id"], qty=0, alloc=0, created=when)
        stocks.append(s); stock_key[k] = s
    stock_key[k]["qty"] += qty
    return stock_key[k]

def new_lot(p, partner, recv):
    if p["shelf"] <= 120 and recv < START + timedelta(days=35):
        mfg = recv - timedelta(days=random.randint(p["shelf"] // 4, p["shelf"] * 2 // 3))   # 초반 짧은 소비기한 입고분 → 일부가 남아 기한 경과
    else:
        mfg = recv - timedelta(days=random.randint(3, min(40, max(5, p["shelf"] // 4))))
    prefix = f"LOT-{mfg:%Y%m%d}-{partner['code']}-"
    lot_seq[prefix] += 1
    code = prefix + f"{lot_seq[prefix]:02d}"
    lot = dict(id=len(lots) + 1, product=p["id"], code=code, expiry=mfg + timedelta(days=p["shelf"]),
               created=datetime.combine(recv, datetime.min.time()) + timedelta(hours=9))
    lots.append(lot); return lot

def at(d, h=9, m=0): return datetime.combine(d, datetime.min.time()) + timedelta(hours=h, minutes=m)

# ---------- 입고 1건 ----------
def make_inbound(t, d, status):
    sup = random.choice(sup_by_t[t])
    created = at(d - timedelta(days=random.randint(1, 3)), random.randint(9, 17))
    doc = dict(id=len(docs) + 1, tenant=t, no=doc_no(t, "IN", created.date()), type="INBOUND",
               source=random.choice(["WMS"] * 5 + ["PORTAL"]), partner=sup["id"], expected=at(d, random.choice([9, 10, 13, 14])),
               ship_addr=None, ship_contact=None, completed=None, status=status, remark=None, created=created)
    docs.append(doc)
    plist = [p for p in prod_by_t[t] if p["active"]]
    chosen = random.sample(plist, random.randint(1, 4))
    stored_any = False
    for idx, p in enumerate(chosen):
        qty = {"A": random.choice([20, 30, 40, 50, 60]), "B": random.choice([40, 60, 80, 100, 120, 160]),
               "C": random.choice([30, 40, 60, 80, 100, 120])}[ZONE_OF[t]]
        lot = new_lot(p, sup, d)
        it = dict(id=len(items) + 1, doc=doc["id"], product=p["id"], lot=lot["id"], qty=qty, remark=None)
        items.append(it)
        if status == "CANCELED": continue
        inspected = status in ("COMPLETED", "INSPECTED") or (status == "WAITING" and d <= TODAY and idx == 0 and random.random() < 0.5)
        if not inspected: continue
        insp_qty = qty
        remark = None
        if random.random() < 0.08:
            insp_qty = qty - random.choice([1, 2, 5]); remark = f"{qty - insp_qty}박스 외박스 파손으로 제외"
        det = dict(id=len(details) + 1, item=it["id"], lot=lot["id"], loc=None, stock=None, qty=insp_qty,
                   status="INSPECTED", remark=remark, created=at(d, 11, random.randint(0, 59)))
        details.append(det)
        store = status == "COMPLETED" or (status == "INSPECTED" and idx == 0 and random.random() < 0.4 and len(chosen) > 1)
        if store:
            l = find_location(t, lot["id"], insp_qty)
            if l is None:
                continue
            s = add_stock(t, lot["id"], l, insp_qty, at(d, 15))
            det.update(loc=l["id"], stock=s["id"], status="STORED")
            stored_any = True
    if status == "COMPLETED":
        if any(x["status"] != "STORED" for x in details if items[x["item"] - 1]["doc"] == doc["id"]):
            doc["status"] = "INSPECTED"   # 칸이 모자라 다 못 넣은 경우
        else:
            doc["completed"] = at(d, 16, random.randint(0, 59))
    if status == "CANCELED":
        doc["remark"] = random.choice(["화주 요청 취소", "발주 수량 변경으로 재등록", "공급사 출하 지연"])
    return doc

# ---------- 출고 ----------
def available_lines(t, p_id, need, when, include_alloc=True):
    """FEFO: 소비기한 빠른 순, 출고일 기준 잔여일 >= 출고허용잔여일"""
    p = products[p_id - 1]
    rows = []
    for s in stocks:
        lot = lots[s["lot"] - 1]
        if lot["product"] != p_id or s["tenant"] != t: continue
        free = s["qty"] - s["alloc"]
        if free <= 0: continue
        if (lot["expiry"] - when).days < p["min_ship"]: continue
        rows.append((lot["expiry"], lot["id"], s["created"], s["id"], s, free))
    rows.sort(key=lambda r: (r[0], r[1], r[2], r[3]))
    out, left = [], need
    for *_, s, free in rows:
        if left <= 0: break
        take = min(free, left); out.append((s, take)); left -= take
    return out, need - left

def make_outbound(t, d, status):
    cus = random.choice(cus_by_t[t])
    created = at(d - timedelta(days=random.randint(1, 4)), random.randint(9, 18))
    doc = dict(id=len(docs) + 1, tenant=t, no=doc_no(t, "OUT", created.date()), type="OUTBOUND",
               source=random.choice(["WMS"] * 4 + ["PORTAL"]), partner=cus["id"], expected=at(d, random.choice([10, 13, 15, 16])),
               ship_addr=cus["addr"], ship_contact=cus["contact"], completed=None, status=status, remark=None, created=created)
    plist = [p for p in prod_by_t[t] if p["active"]]
    chosen = random.sample(plist, random.randint(1, 4))
    lines = []
    for p in chosen:
        qty = {"A": random.choice([5, 8, 10, 12, 15, 20]), "B": random.choice([10, 20, 24, 30, 40]),
               "C": random.choice([10, 15, 20, 25, 30])}[ZONE_OF[t]]
        lines.append((p, qty))
    if status in ("SHIPPED", "PICKING", "ALLOCATED"):
        planned = []
        for p, qty in lines:
            got, n = available_lines(t, p["id"], qty, d if status == "SHIPPED" else TODAY)
            if n == 0: continue
            planned.append((p, n, got))
        if not planned:
            return None
        docs.append(doc)
        partial = status == "ALLOCATED" and random.random() < 0.25 and len(planned) > 1
        for i, (p, n, got) in enumerate(planned):
            it = dict(id=len(items) + 1, doc=doc["id"], product=p["id"], lot=None, qty=n, remark=None)
            items.append(it)
            if partial and i == len(planned) - 1:
                doc["remark"] = "일부 품목 재고 대기 (부분 할당)"
                continue
            for s, take in got:
                det = dict(id=len(details) + 1, item=it["id"], lot=s["lot"], loc=s["loc"], stock=s["id"], qty=take,
                           status="ALLOCATED", remark=None, created=at(d if status == "SHIPPED" else TODAY - timedelta(days=1), 10))
                details.append(det)
                if status == "SHIPPED":
                    s["qty"] -= take; det["status"] = "SHIPPED"
                else:
                    s["alloc"] += take
        if status == "SHIPPED":
            doc["completed"] = at(d, 17, random.randint(0, 59))
        return doc
    # WAITING / CANCELED
    docs.append(doc)
    for p, qty in lines:
        items.append(dict(id=len(items) + 1, doc=doc["id"], product=p["id"], lot=None, qty=qty, remark=None))
    if status == "CANCELED":
        doc["remark"] = random.choice(["고객 주문 취소", "배송지 변경으로 재등록", "중복 주문"])
    return doc

# ---------------- 날짜순 진행 ----------------
d = START
n_in = n_out = 0
while d <= TODAY + timedelta(days=6):
    past = d < TODAY - timedelta(days=8)
    future = d > TODAY
    for t in (1, 2, 3):
        # 입고
        if random.random() < (0.85 if d < TODAY else 0.6):
            if future: st = "WAITING"
            elif past: st = random.choices(["COMPLETED", "CANCELED"], [95, 5])[0]
            else: st = random.choices(["COMPLETED", "INSPECTED", "WAITING", "CANCELED"], [40, 30, 25, 5])[0]
            make_inbound(t, d, st); n_in += 1
    for t in (1, 2, 3):
        # 출고 (입고가 쌓인 뒤부터)
        if d >= START + timedelta(days=5) and random.random() < (0.95 if d < TODAY else 0.8):
            if future: st = "WAITING"
            elif past: st = random.choices(["SHIPPED", "CANCELED"], [95, 5])[0]
            else: st = random.choices(["SHIPPED", "PICKING", "ALLOCATED", "WAITING", "CANCELED"], [25, 30, 30, 10, 5])[0]
            if st in ("PICKING", "ALLOCATED"):
                pass  # 아래 마지막 단계에서 오늘 재고로 할당
            doc = make_outbound(t, d, st if st not in ("PICKING", "ALLOCATED") else "WAITING")
            if doc is not None:
                doc["_want"] = st
            n_out += 1
    d += timedelta(days=1)

# 진행 중 출고: 오늘 재고 기준으로 할당 (WAITING 으로 만들어 둔 것 중 _want 가 ALLOCATED / PICKING)
for doc in docs:
    want = doc.pop("_want", None)
    if want not in ("ALLOCATED", "PICKING"): continue
    its = [i for i in items if i["doc"] == doc["id"]]
    any_alloc = False
    partial = want == "ALLOCATED" and random.random() < 0.25 and len(its) > 1
    for k, it in enumerate(its):
        if partial and k == len(its) - 1:
            doc["remark"] = "일부 품목 재고 대기 (부분 할당)"; continue
        got, n = available_lines(doc["tenant"], it["product"], it["qty"], TODAY)
        if n < it["qty"]:
            if n == 0: continue
            it["qty"] = n
        for s, take in got:
            details.append(dict(id=len(details) + 1, item=it["id"], lot=s["lot"], loc=s["loc"], stock=s["id"], qty=take,
                                status="ALLOCATED", remark=None, created=NOW - timedelta(hours=random.randint(2, 30))))
            s["alloc"] += take; any_alloc = True
    if any_alloc:
        doc["status"] = want
for doc in docs:
    doc.pop("_want", None)

# 미할당 품목이 있는 문서는 피킹 시작 불가 → PICKING 은 전 품목 할당된 것만
def doc_items(doc): return [i for i in items if i["doc"] == doc["id"]]
def doc_details(doc):
    ids = {i["id"] for i in doc_items(doc)}
    return [x for x in details if x["item"] in ids]
def fully_allocated(doc):
    for it in doc_items(doc):
        if sum(x["qty"] for x in details if x["item"] == it["id"]) < it["qty"]: return False
    return True
for doc in docs:
    if doc["status"] == "PICKING" and not fully_allocated(doc):
        doc["status"] = "ALLOCATED"

# ---------------- 피킹 리스트 ----------------
pk_seq = defaultdict(int)
def pk_no(dd):
    pk_seq[dd] += 1; return f"PK-{dd:%Y%m%d}-{pk_seq[dd]:03d}"
def make_plist(doc, status, when):
    dets = sorted(doc_details(doc), key=lambda x: locations[x["loc"] - 1]["code"])
    worker = random.choice([2, 3, 4]) if status != "CREATED" else None
    pl = dict(id=len(plists) + 1, no=pk_no(when.date()), doc=doc["id"], worker=worker, status=status,
              assigned=when + timedelta(minutes=20) if worker else None,
              started=when + timedelta(minutes=40) if status in ("PICKING", "DONE") else None,
              completed=when + timedelta(hours=2) if status == "DONE" else None, created=when)
    plists.append(pl)
    for seq, x in enumerate(dets, 1):
        picked = 0; picked_at = None
        if status == "DONE" or x["status"] in ("PICKED",):
            picked = x["qty"]; picked_at = when + timedelta(minutes=40 + seq * 4)
        pitems.append(dict(id=len(pitems) + 1, pl=pl["id"], det=x["id"], seq=seq, picked=picked, picked_at=picked_at))
    return pl

# 출고 완료 중 최근 것 일부는 QR 로 처리된 기록 (DONE)
shipped_recent = [x for x in docs if x["type"] == "OUTBOUND" and x["status"] == "SHIPPED" and x["completed"] and x["completed"].date() >= TODAY - timedelta(days=12)]
for doc in random.sample(shipped_recent, min(10, len(shipped_recent))):
    make_plist(doc, "DONE", doc["completed"] - timedelta(hours=4))
# 피킹 중: 일부 줄은 다 집음(PICKED), 나머지는 할당 상태
for doc in [x for x in docs if x["status"] == "PICKING"]:
    dets = doc_details(doc)
    k = max(1, len(dets) // 2)
    for x in dets[:k]: x["status"] = "PICKED"
    if random.random() < 0.6:
        pl = make_plist(doc, "PICKING", NOW - timedelta(hours=random.randint(1, 5)))
        # 덜 집은 줄 하나는 일부만 집은 상태
        rest = [pi for pi in pitems if pi["pl"] == pl["id"] and pi["picked"] == 0]
        if rest:
            r = rest[0]; dq = details[r["det"] - 1]["qty"]
            if dq > 1: r["picked"] = dq // 2
# 할당 완료(전 품목) 문서 일부: QR 리스트 생성·배정 대기
alloc_full = [x for x in docs if x["status"] == "ALLOCATED" and fully_allocated(x)]
for i, doc in enumerate(random.sample(alloc_full, min(8, len(alloc_full)))):
    make_plist(doc, "ASSIGNED" if i % 2 == 0 else "CREATED", NOW - timedelta(hours=random.randint(1, 20)))

# ---------------- 폐기 ----------------
expired = [s for s in stocks if s["qty"] > 0 and s["alloc"] == 0 and lots[s["lot"] - 1]["expiry"] < TODAY]
random.shuffle(expired)
for s in expired[:max(0, len(expired) - 3)]:   # 3건은 폐기 버튼 시연용으로 남김
    lot = lots[s["lot"] - 1]
    when = at(min(TODAY - timedelta(days=1), lot["expiry"] + timedelta(days=random.randint(1, 3))), 17, 30)
    disposals.append(dict(id=len(disposals) + 1, stock=s["id"], qty=s["qty"], reason="소비기한 경과", user=1, at=when))
    s["qty"] = 0

# ---------------- 검증 ----------------
for s in stocks:
    assert 0 <= s["alloc"] <= s["qty"], s
for l in locations:
    if l["cap"] is not None: assert loc_used(l) <= l["cap"], l
    ts = {s["tenant"] for s in stocks if s["loc"] == l["id"] and s["qty"] > 0}
    assert len(ts) <= 1, l
for s in stocks:
    exp_alloc = sum(x["qty"] for x in details if x["stock"] == s["id"] and x["status"] in ("ALLOCATED", "PICKED")
                    and docs[items[x["item"] - 1]["doc"] - 1]["type"] == "OUTBOUND")
    assert exp_alloc == s["alloc"], (s, exp_alloc)
for doc in docs:
    if doc["type"] == "INBOUND" and doc["status"] == "COMPLETED":
        assert all(x["status"] == "STORED" for x in doc_details(doc)) and len(doc_details(doc)) == len(doc_items(doc))
    if doc["type"] == "INBOUND" and doc["status"] == "INSPECTED":
        assert len(doc_details(doc)) == len(doc_items(doc)), doc
    if doc["type"] == "OUTBOUND" and doc["status"] in ("WAITING", "CANCELED"):
        assert not doc_details(doc)
assert len({d["no"] for d in docs}) == len(docs)
assert len({(l["product"], l["code"]) for l in lots}) == len(lots)

# ---------------- SQL 출력 ----------------
out = []
w = out.append
cnt = lambda st, typ: sum(1 for x in docs if x["status"] == st and x["type"] == typ)
w("-- ============================================================")
w("-- R__seed_demo.sql — 시연용 시드 (Flyway 반복 마이그레이션, db/seed)")
w("-- 생성: scripts/seed/gen.py (직접 고치지 말고 생성기를 고쳐서 다시 만든다)")
w("-- 기준 시각: 2026-10-14 10:00 · 이 파일 내용이 바뀌면 Flyway 가 다시 실행 = 데이터 전부 초기화")
w("-- 시연 리셋: scripts/seed/reset-demo.sh 가 이 파일을 그대로 다시 실행")
w("--")
w(f"-- 화주 3 · 품목 {len(products)} · 거래처 {len(partners)} · 칸 {len(locations)} (A·B·C 각 통로 4 × 베이 10)")
w(f"-- 계정 {len(users)} (admin 은 별도 비밀번호, worker01~03 은 공통 비밀번호. 둘 다 BCrypt 해시)")
w(f"-- LOT {len(lots)} · 재고 {len(stocks)}행 · 문서 {len(docs)} · 문서 품목 {len(items)} · 검수/할당 줄 {len(details)}")
w(f"-- 입고 {sum(1 for x in docs if x['type']=='INBOUND')}: 완료 {cnt('COMPLETED','INBOUND')} / 검수완료 {cnt('INSPECTED','INBOUND')} / 예정 {cnt('WAITING','INBOUND')} / 취소 {cnt('CANCELED','INBOUND')}")
w(f"-- 출고 {sum(1 for x in docs if x['type']=='OUTBOUND')}: 출고완료 {cnt('SHIPPED','OUTBOUND')} / 피킹중 {cnt('PICKING','OUTBOUND')} / 할당 {cnt('ALLOCATED','OUTBOUND')} / 예정 {cnt('WAITING','OUTBOUND')} / 취소 {cnt('CANCELED','OUTBOUND')}")
w(f"-- 피킹 리스트 {len(plists)} (DONE {sum(1 for p in plists if p['status']=='DONE')} / PICKING {sum(1 for p in plists if p['status']=='PICKING')} / ASSIGNED {sum(1 for p in plists if p['status']=='ASSIGNED')} / CREATED {sum(1 for p in plists if p['status']=='CREATED')}) · 폐기 {len(disposals)}건")
w(f"-- 칸: A 용량 150 / B 240 / C 200, B-04-10·C-04-10 무제한, 사용 중지 3칸. 한 칸에는 한 화주만")
w("-- 재고 수량 = 적재 합 − 출고 합 − 폐기, 선점 = 할당·피킹중 문서의 할당 합 (생성기에서 검증)")
w("-- ============================================================")
w("")
w("SET FOREIGN_KEY_CHECKS = 0;")
for tb in ("picking_list_item", "picking_list", "stock_disposal", "document_item_detail", "document_item", "document",
           "stock", "lot", "location", "partner", "product", "user_account", "tenant"):
    w(f"TRUNCATE TABLE {tb};")
w("SET FOREIGN_KEY_CHECKS = 1;")
w("")

def ins(table, cols, rows, chunk=200):
    if not rows: return
    for i in range(0, len(rows), chunk):
        w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
        part = rows[i:i + chunk]
        w(",\n".join("(" + ", ".join(q(v) for v in r) + ")" for r in part) + ";")
    w("")

ins("tenant", ["tenant_id", "tenant_code", "tenant_name"], [(t[0], t[1], t[2]) for t in TENANTS])
ins("user_account", ["user_id", "login_id", "password", "user_name", "role", "is_active"],
    [(u["id"], u["login"], ADMIN_PW if u["role"] == "ADMIN" else PW, u["name"], u["role"], True) for u in users])
ins("product", ["product_id", "tenant_id", "product_code", "product_name", "spec", "unit", "min_ship_days", "is_active"],
    [(p["id"], p["tenant"], p["code"], p["name"], p["spec"], p["unit"], p["min_ship"], p["active"]) for p in products])
ins("partner", ["partner_id", "tenant_id", "partner_code", "partner_name", "partner_type", "contact", "address", "is_active"],
    [(p["id"], p["tenant"], p["code"], p["name"], p["type"], p["contact"], p["addr"], p["active"]) for p in partners])
ins("location", ["location_id", "location_code", "capacity", "remark", "is_active"],
    [(l["id"], l["code"], l["cap"], l["remark"], l["active"]) for l in locations])
ins("lot", ["lot_id", "product_id", "lot_code", "expiry_date", "created_at", "updated_at"],
    [(l["id"], l["product"], l["code"], l["expiry"], l["created"], l["created"]) for l in lots])
ins("document", ["document_id", "tenant_id", "document_no", "type", "source", "partner_id", "expected_at", "ship_address",
                 "ship_contact", "completed_at", "status", "remark", "created_at", "updated_at"],
    [(x["id"], x["tenant"], x["no"], x["type"], x["source"], x["partner"], x["expected"], x["ship_addr"], x["ship_contact"],
      x["completed"], x["status"], x["remark"], x["created"], x["completed"] or x["created"]) for x in docs])
ins("document_item", ["document_item_id", "document_id", "product_id", "lot_id", "expected_qty", "remark"],
    [(i["id"], i["doc"], i["product"], i["lot"], i["qty"], i["remark"]) for i in items])
ins("stock", ["stock_id", "tenant_id", "lot_id", "location_id", "qty", "allocated_qty", "created_at"],
    [(s["id"], s["tenant"], s["lot"], s["loc"], s["qty"], s["alloc"], s["created"]) for s in stocks])
ins("document_item_detail", ["detail_id", "document_item_id", "lot_id", "location_id", "stock_id", "qty", "status", "remark", "created_at", "updated_at"],
    [(x["id"], x["item"], x["lot"], x["loc"], x["stock"], x["qty"], x["status"], x["remark"], x["created"], x["created"]) for x in details])
ins("stock_disposal", ["disposal_id", "stock_id", "qty", "reason", "user_id", "disposed_at"],
    [(x["id"], x["stock"], x["qty"], x["reason"], x["user"], x["at"]) for x in disposals])
ins("picking_list", ["picking_list_id", "picking_no", "document_id", "worker_id", "status", "assigned_at", "started_at", "completed_at", "created_at"],
    [(p["id"], p["no"], p["doc"], p["worker"], p["status"], p["assigned"], p["started"], p["completed"], p["created"]) for p in plists])
ins("picking_list_item", ["picking_list_item_id", "picking_list_id", "detail_id", "seq", "picked_qty", "picked_at"],
    [(p["id"], p["pl"], p["det"], p["seq"], p["picked"], p["picked_at"]) for p in pitems])

import sys
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
print("\n".join(out[6:12]))