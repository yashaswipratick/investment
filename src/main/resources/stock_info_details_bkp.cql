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

CREATE TYPE IF NOT EXISTS metadata (
series text,
symbol text,
isin text,
status text,
listingDate text,
industry text,
lastUpdateTime text,
pdSectorPe double,
pdSymbolPe double,
pdSectorInd text
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
faceValue double,
issuedSize bigint
);

CREATE TYPE IF NOT EXISTS sdd_details (
SDDAuditor text,
SDDStatus text
);

CREATE TYPE IF NOT EXISTS price_info (
lastPrice double,
change double,
pChange double,
previousClose double,
open double,
close double,
vwap double,
lowerCP double,
upperCP double,
pPriceBand text,
basePrice double,
intraDayHighLow map<text, double>,
weekHighLow map<text, double>,
iNavValue double,
checkINAV boolean
);

CREATE TYPE IF NOT EXISTS industry_info (
macro text,
sector text,
industry text,
basicIndustry text
);

CREATE TYPE IF NOT EXISTS pre_open_market (
preopen list<frozen<map<text, double>>>,
ato frozen<ato>,
IEP double,
totalTradedVolume bigint,
finalPrice double,
finalQuantity bigint,
lastUpdateTime text,
totalBuyQuantity bigint,
totalSellQuantity bigint,
atoBuyQty bigint,
atoSellQty bigint,
Change double,
perChange double,
prevClose double
);


CREATE TYPE IF NOT EXISTS ato (
buy bigint,
sell bigint
);

CREATE TYPE IF NOT EXISTS pre_open (
price double,
buyQty bigint,
sellQty bigint,
iep boolean
);

CREATE TYPE IF NOT EXISTS intra_day_high_low (
min double,
max double,
value double
);

CREATE TYPE IF NOT EXISTS surveillance (
surv text,
"desc" text
);

CREATE TYPE IF NOT EXISTS week_high_low (
min double,
minDate text,
max double,
maxDate text,
value double
);

CREATE TYPE IF NOT EXISTS stock_info_dto (
info frozen<info>,
metadata frozen<metadata>,
security_info frozen<security_info>,
sdd_details frozen<sdd_details>,
price_info frozen<price_info>,
industry_info frozen<industry_info>,
pre_open_market frozen<pre_open_market>
);

CREATE TABLE IF NOT EXISTS stock_info_details (
key text PRIMARY KEY,
stock_info frozen<stock_info_dto>,
created_date timestamp
);
