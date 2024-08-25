CREATE TYPE IF NOT EXISTS ato (
    buy int,
    sell int
);

CREATE TYPE IF NOT EXISTS industry_info (
    macro text,
    sector text,
    industry text,
    basicIndustry text
);

CREATE TYPE IF NOT EXISTS info (
    symbol text,
    companyName text,
    industry text,
    activeSeries list<text>,
    debtSeries list<text>,
    isFNOSec boolean,
    isCASec boolean,
    isSLBSec boolean,
    isDebtSec boolean,
    isSuspended boolean,
    tempSuspendedSeries list<text>,
    isETFSec boolean,
    isDelisted boolean,
    isin text,
    isMunicipalBond boolean,
    isTop10 boolean,
    identifier text
);

CREATE TYPE IF NOT EXISTS intra_day_high_low (
    min text,
    max text,
    value text
);

CREATE TYPE IF NOT EXISTS metadata (
    series text,
    symbol text,
    isin text,
    status text,
    listingDate text,
    industry text,
    lastUpdateTime text,
    pdSectorPe text,
    pdSymbolPe text,
    pdSectorInd text
);

CREATE TYPE IF NOT EXISTS pre_open (
    price text,
    buyQty int,
    sellQty int,
    iep boolean
);

CREATE TYPE IF NOT EXISTS surveillance (
    surv text,
    "desc" text
);

CREATE TYPE IF NOT EXISTS week_high_low (
    min text,
    minDate text,
    max text,
    maxDate text,
    value text
);

CREATE TYPE IF NOT EXISTS price_info (
    lastPrice text,
    change text,
    pChange text,
    previousClose text,
    open text,
    close text,
    vwap text,
    lowerCP text,
    upperCP text,
    pPriceBand text,
    basePrice text,
    intraDayHighLow frozen<intra_day_high_low>,
    weekHighLow frozen<week_high_low>,
    iNavValue text,
    checkINAV boolean
);

CREATE TYPE IF NOT EXISTS sdd_details (
    SDDAuditor text,
    SDDStatus text
);

CREATE TYPE IF NOT EXISTS security_info (
    boardStatus text,
    tradingStatus text,
    tradingSegment text,
    sessionNo text,
    slb text,
    classOfShare text,
    derivatives text,
    surveillance frozen<surveillance>,
    faceValue int,
    issuedSize bigint
);

CREATE TYPE IF NOT EXISTS pre_open_market (
    preopen list<frozen<pre_open>>,
    ato frozen<ato>,
    IEP text,
    totalTradedVolume int,
    finalPrice text,
    finalQuantity int,
    lastUpdateTime text,
    totalBuyQuantity int,
    totalSellQuantity int,
    atoBuyQty int,
    atoSellQty int,
    Change text,
    perChange text,
    prevClose text
);

CREATE TYPE IF NOT EXISTS stock_info_dto (
    info frozen<info>,
    metadata frozen<metadata>,
    securityInfo frozen<security_info>,
    sddDetails frozen<sdd_details>,
    priceInfo frozen<price_info>,
    industryInfo frozen<industry_info>,
    preOpenMarket frozen<pre_open_market>
);

CREATE TABLE IF NOT EXISTS stock_info_details (
    key text PRIMARY KEY,
    stock_info map<text, frozen<stock_info_dto>>,
    created_date timestamp
);