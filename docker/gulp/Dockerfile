FROM node:8.4

ENV http_proxy=http://dev-proxy.db.rakuten.co.jp:9501
ENV https_proxy=http://dev-proxy.db.rakuten.co.jp:9501

RUN yarn install
RUN yarn global add gulp-cli

RUN npm config set proxy http://pkg.proxy.prod.jp.local:10080 \
        && npm config set https-proxy http://pkg.proxy.prod.jp.local:10080
