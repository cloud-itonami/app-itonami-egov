# Public procurement (tender) sources usable with no credentials and no payment

Inventory date: 2026-11-23. Scope: sources a program can query or download
with **no account, no API key and no payment**, verified against the
operator's own terms / API documentation on that date.

Rules applied in this inventory:
- Only sources whose terms or API documentation were actually opened are
  scored. Anything not opened is marked "not verified".
- No notice text, screenshots, or procurement-officer contact details are
  reproduced here. Contact desks exist on some terms pages; they are not
  copied into this repo.
- No download was run for this inventory; endpoints below are documented,
  not exercised.

## Table

| source id | operator | primary URL (terms) | API URL | credentials | cost | bulk vs query | update cadence | attribution required | person data present | verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| eu-ted | Publications Office of the EU (TED) | https://ted.europa.eu/en/legal-notice | https://api.ted.europa.eu/v3/notices/search (POST, JSON) | none for search | free | both: anonymous search API (query) + daily/monthly XML bulk packages, no sign-in | daily edition Mon–Fri; daily XML package by 09:30 on publication day, monthly by 5th working day | notices: freely reusable, commercial or non-commercial; site editorial content CC BY 4.0; system metadata CC0 | yes — notices carry buyer contact details (fields exist; none copied here) | included |
| jp-kkj | 中小企業庁 (Small and Medium Enterprise Agency) — 官公需情報ポータルサイト | https://www.kkj.go.jp/s/?tc=1 | https://www.kkj.go.jp/api/ (GET/POST, XML) | none; guide states no user-ID parameter is provided at present | free | query only (Count capped at 1,000 per call); no bulk file documented | notices enter the database about 1 day after the buying body's own publication | yes — using the API requires stating that the portal API is used and linking to the portal | not in the documented XML fields (organisation, project, dates, codes; no named-contact fields listed) | included |
| us-sam | U.S. General Services Administration (SAM.gov) | https://sam.gov/about/terms-of-use | https://api.sam.gov/opportunities/v2/search | **API key required** — api_key is a mandatory parameter; key is requested from the Account Details page of a registered SAM.gov account (password needed to view it) | free | query only (postedFrom/postedTo mandatory, max 1-year window); Data Services downloads exist but are behind the same account/API-key terms; scraping prohibited | active notices updated daily, archived weekly (per API page) | not verified for API reuse beyond the account terms opened | yes — response includes point-of-contact name/email/phone/fax fields | excluded (credentials) |
| uk-ftender | Cabinet Office (Find a Tender service) | https://www.find-tender.service.gov.uk/Developer/Documentation | https://www.find-tender.service.gov.uk/api/1.0/ocdsReleasePackages (GET, OCDS JSON) | none stated for the OCDS read API (the Basic-Auth endpoint in the docs is the separate eSender submission search, not the data API) | free | query (limit 1–100, cursor paging, updatedFrom/updatedTo, stages) + daily XML zips via data.gov.uk | not verified — publication cadence not stated on the pages opened; API filters by updated timestamps | yes — Open Government Licence v3.0: "Contains public sector information licensed under the Open Government Licence v3.0." | yes — OCDS party records carry contact points (none copied here) | included |
| uk-cfinder | Cabinet Office (Contracts Finder) | https://www.gov.uk/government/publications/open-contracting | https://www.contractsfinder.service.gov.uk/Published/Notices/OCDS/Search (GET, OCDS JSON) | none stated on the pages opened | free | query (limit/cursor) + dated CSV harvest endpoint | not verified | yes — same OGL v3.0 as Find a Tender | not verified for this row (fields not opened) | included |
| un-ungm | United Nations / UNOPS (UN Global Marketplace) | https://ungm.org/Public/Pages/Terms | https://www.ungm.org/Public/Notice (web search); developer API at https://developer.ungm.org (OData, bearer token) | developer API: **access token required** (own-agency notices); public web search needs no login, but see terms | free to browse; UNGM Pro / Tender Alert Service is a paid subscription | query only (web filters); no bulk download documented | not verified | reuse prohibited without prior written permission: materials may not be used for commercial or public use, reproduced, stored, transmitted or disseminated without consent of the respective organisation | yes — notices may name contacts (none copied here) | excluded (credentials) |
| wb-proc | World Bank Group | https://worldbank.org/ext/en/legal/terms-conditions | https://search.worldbank.org/api/v2/procnotices (GET, JSON) | none observed — the endpoint returned JSON without a key when opened | free | query (qterm, country, notice type, date filters, rows paging); no bulk file documented on the pages opened | not verified | yes — attribution to the World Bank Group in the prescribed wording; **content is provided for informational and non-commercial purposes only unless otherwise stated** | not verified — notice text was not analysed for personal data | included (non-commercial only) |
| sg-gebiz | Defence Science and Technology Agency (DSTA) / Ministry of Finance (GeBIZ) | https://www.gebiz.gov.sg/terms-of-use.html | no public API documented; web search at https://www.gebiz.gov.sg/ptn/opportunity ; RSS feeds | browsing/search needs no login; **responding, downloading tender documents and subscribing need a GeBIZ Trading Partner account** (CorpPass/Singpass), with annual fees for additional representative accounts | browsing and RSS free; Trading Partner accounts carry annual subscription fees beyond the first authorised representative | query only (web search); RSS by category — RSS terms forbid editing/modifying the feed content and hyperlinking/framing without permission | not verified | contents "shall not be reproduced, republished, uploaded, posted, transmitted or otherwise distributed in any way, without the prior written permission of DSTA" | not verified | excluded (credentials) |
| au-austender | Department of Finance (AusTender) | https://tenders.gov.au/infolinks/termsofuse | https://api.tenders.gov.au/ocds/ (GET, OCDS JSON; findById / findByDates) | none stated for the OCDS API; portal registration is required only to download ATM documentation and lodge responses | free | query by Contract Notice ID or date window; historical bulk files via data.gov.au (1999–2020) | weekly Contract Notice export files for the last 18 months (per data.gov.au dataset page); OCDS API cadence not verified | yes — prescribed attribution: materials released by the Department of Finance, verify on tenders.gov.au. **Licence is CC BY 3.0 AU except Approach to Market (ATM) material, which is excluded from reuse without prior written approval** | yes — OCDS party records can carry supplier/buyer address details (none copied here) | included (Contract Notices only; ATM material excluded) |
| ca-canadabuys | Public Services and Procurement Canada (CanadaBuys) | https://open.canada.ca/en/open-government-licence-canada | bulk CSVs listed at https://open.canada.ca/data/en/dataset/6abd20d4-7a1c-4b38-baa2-9525d0bb2fd2 (no query API documented on the pages opened) | none | free | bulk only: open-tender CSV, new-tender (last 24 h) CSV, per-fiscal-year tender and award CSVs, complete file since 2022-08-08 | refreshed each morning between 07:00 and 08:30 (UTC−05:00) | yes — acknowledge the source; default statement "Contains information licensed under the Open Government Licence – Canada." | not verified — the licence expressly exempts personal information, and the dataset page opened does not enumerate the CSV fields | included |

## Notes per source

- **eu-ted** — The TED search API "can be accessed anonymously" and the
  Swagger index states it "is accessible to the general public … in
  accordance with the term of usage". Bulk daily/monthly XML packages need
  no sign-in. Fair-usage limits apply (600 views/downloads per IP in under
  6 minutes, 700 requests per minute, 3 concurrent package downloads). A
  separate CSV subset exists on data.europa.eu but was not opened, so it is
  not scored here.
- **jp-kkj** — The API guide (V1.1, 2016-05-27) documents parameters
  Query / Project_Name / Organization_Name / LG_Code / Category /
  Procedure_Type / Certification / date windows, XML output, and states
  user-ID parameters are not provided at present. The terms require the
  using site/app to state it uses the portal API and to link to the
  portal, and forbid sustained heavy access. The guide is old (2016); the
  endpoint and anonymity were taken as documented, not re-tested.
- **us-sam** — Web search at sam.gov needs no account ("Anyone may search
  contract opportunities without an account"), but the programmatic route
  does: the GSA API page marks api_key as required and describes requesting
  it from a registered account. SAM terms also prohibit automated scraping
  outside the permitted API/data-services routes. Hence excluded under the
  no-credentials rule even though the key itself is free.
- **uk-ftender / uk-cfinder** — Both are Cabinet Office OCDS outputs under
  the Open Government Licence v3.0. Find a Tender also publishes daily XML
  zips via data.gov.uk. The only authenticated endpoint seen in the docs is
  the eSender submission search (Basic Auth) — that is for senders, not for
  reading notices, and is not scored as a data-source requirement.
- **un-ungm** — Public notices are browsable without login, but the terms
  restrict use to personal, lawful purposes, forbid commercial/public use
  and any dissemination or inclusion in a retrieval system without prior
  written permission, and the developer API requires a bearer token for an
  agency's own notices. Browsing is not reuse; excluded.
- **wb-proc** — The procnotices endpoint answered without a key. The legal
  page limits site content to informational, non-commercial use unless
  otherwise stated and requires prescribed attribution. Dataset-specific
  terms (Data Catalog) were not opened, so any dataset-level licence beyond
  the site terms is "not verified".
- **sg-gebiz** — Opportunities and award results can be browsed without an
  account, and RSS is free, but the Terms of Use prohibit reproducing or
  redistributing site contents without DSTA's prior written permission,
  and the transactional account (Trading Partner) requires CorpPass/Singpass
  plus annual fees for additional representatives. No open API. Excluded.
- **au-austender** — The OCDS API (api.tenders.gov.au/ocds) is documented
  without an API key. The reuse licence (CC BY 3.0 AU) covers Contract
  Notices; Approach to Market material is carved out entirely (no
  reproduction, re-transmission, distribution or commercialisation without
  prior written approval, and no use to sell a product/service or drive
  traffic). Only Contract Notice data is in scope for this inventory.
- **ca-canadabuys** — Bulk CSVs under the Open Government Licence – Canada,
  refreshed daily in the morning. No query API was documented on the pages
  opened, so this row is bulk-only. Licence attribution wording above is
  the licence's default statement.

## URLs opened (2026-11-23)

Primary terms / API documentation actually opened for the table above:

- https://ted.europa.eu/en/legal-notice
- https://ted.europa.eu/en/simap/developers-corner-for-reusers
- https://ted.europa.eu/en/help/data-reuse
- https://ted.europa.eu/api/documentation/index.html
- https://www.kkj.go.jp/s/?tc=1
- https://kkj.go.jp/doc/ja/api_guide.pdf
- https://sam.gov/about/terms-of-use
- https://open.gsa.gov/api/get-opportunities-public-api
- https://www.find-tender.service.gov.uk/Developer/Documentation
- https://www.find-tender.service.gov.uk/apidocumentation/1.0/GET-ocdsReleasePackages
- https://www.gov.uk/government/publications/open-contracting
- https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
- https://ungm.org/Public/Pages/Terms
- https://www.ungm.org/Public/Notice
- https://developer.ungm.org/Article/SearchNotices
- https://worldbank.org/ext/en/legal/terms-conditions
- https://search.worldbank.org/api/v2/procnotices?format=json&rows=1
- https://projects.worldbank.org/en/projects-operations/procurement-search
- https://www.gebiz.gov.sg/terms-of-use.html
- https://www.gebiz.gov.sg/rss-terms-of-use.html
- https://www.gebiz.gov.sg/ptn/opportunity
- https://tenders.gov.au/infolinks/termsofuse
- https://github.com/austender/austender-ocds-api
- https://data.gov.au/data/dataset/historical-australian-government-contract-data
- https://open.canada.ca/data/en/dataset/6abd20d4-7a1c-4b38-baa2-9525d0bb2fd2
- https://open.canada.ca/en/open-government-licence-canada

Pages that returned 404 / fetch errors and were therefore **not** used as
evidence (recorded so they are not mistaken for opened terms): the initial
TED web-help and v3-docs guesses, api.data.gov SAM page, the old
find-tender `/Api/Description` path, the old gov.uk FATS publication path,
ungm.org/Public/Notices (plural), the World Bank notice-of-procurement and
debarred-firms paths tried, tenders.gov.au/Terms, and the kkj.go.jp root
(terms were instead opened at the `?tc=1` URL above).

No other source was added: candidate extras without an opened primary
terms page (e.g. the TED CSV subset on data.europa.eu, UN Development
Business) are left out rather than scored from secondary summaries.
