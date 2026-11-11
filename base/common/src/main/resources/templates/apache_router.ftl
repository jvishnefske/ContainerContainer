
<#if addHa && addOnce>
## add HA entries when ha is configured
<Proxy balancer://${upstreamName}>
    <#list hsservers as haserver>
    ${haserver}
    </#list>
ProxySet lbmethod=byrequests
ProxySet failonstatus=503
</Proxy>
<Proxy balancer://artifactory-direct>
    ## servers with the Artifactory ports. Note that the port is the default one, if you configured differently it must be changed
    <#list hsServersDirect as haServerDirect>
    ${haServerDirect}
    </#list>
ProxySet lbmethod=byrequests
ProxySet failonstatus=503
</Proxy>
</#if>
<#if !generalOnly>

Listen ${repoPort}
</#if>
<VirtualHost *:<#if httpOnly && generalOnly>${httpPort}</#if><#if !generalOnly>${repoPort}</#if><#if httpsOnly && generalOnly>${sslPort}</#if>>

    ProxyPreserveHost On
    ProxyAddHeaders off

    ServerName ${serverName}
    ServerAlias *.${serverName}
    ServerAdmin server@admin

<#if useHttps && !httpOnly>
    SSLEngine on
    SSLCertificateFile ${sslCrtPath}
    SSLCertificateKeyFile ${sslKeyPath}
    SSLProxyEngine on
</#if>

    ## Application specific logs
    ## ErrorLog ${APACHE_LOG_DIR}/${serverName}-error.log
    ## CustomLog ${APACHE_LOG_DIR}/${serverName}-access.log combined

    AllowEncodedSlashes On
    RewriteEngine on

    RewriteRule (.*) - [E=CLIENT_IP:%{REMOTE_ADDR}]

    RewriteCond %{SERVER_PORT} (.*)
    RewriteRule (.*) - [E=my_server_port:%1]
    ##  NOTE: The 'REQUEST_SCHEME' Header is supported only from apache version 2.4 and above
    RewriteCond %{REQUEST_SCHEME} (.*)
    RewriteRule (.*) - [E=my_scheme:%1]

    RewriteCond %{HTTP_HOST} (.*)
    RewriteRule (.*) - [E=my_custom_host:%1]

<#if subdomain>
    RewriteCond "%{REQUEST_URI}" "^/(v1|v2)/"
    RewriteCond "%{HTTP_HOST}" ${quotes}^(.*)\.${serverName}$${quotes}
    RewriteRule "^/(v1|v2)/(.*)$" ${quotes}/artifactory/api/docker/%1/$1/$2${quotes} [PT]
</#if>
<#if (!subdomain && !generalOnly && !isRepoPath) || isSamePort>
    RewriteRule "^/(v1|v2)/(.*)$" ${quotes}/artifactory/api/docker/${repoKey}/$1/$2${quotes} [PT]
</#if>

    RewriteRule ^(/)?$      /ui/ [R,L]
    RewriteRule ^/ui$      /ui/ [R,L]

    RequestHeader set Host %{my_custom_host}e
    RequestHeader set X-Forwarded-Port %{my_server_port}e
    ## NOTE: {my_scheme} requires a module which is supported only from apache version 2.4 and above
    RequestHeader set X-Forwarded-Proto %{my_scheme}e
    RequestHeader set X-Forwarded-For %{CLIENT_IP}e
    RequestHeader set X-JFrog-Override-Base-Url %{my_scheme}e://${serverName}:%{my_server_port}e
    Header always set X-Content-Type-Options "nosniff"
<#if useHttps && !httpOnly>
    Header always set Strict-Transport-Security ""
</#if>
    ProxyPassReverseCookiePath / /

<#if addGeneral>
    ProxyRequests off
    ProxyPreserveHost on
</#if>
<#if !addHa>
    ProxyPass "/artifactory/" http://${localNameAndPortArti}/artifactory/
    ProxyPassReverse "/artifactory/" http://${localNameAndPortArti}/artifactory/
    ProxyPass "/" http://${localNameAndPort}/ nocanon
    ProxyPassReverse "/" http://${localNameAndPort}/
</#if>
<#if addHa>
    ProxyPass "/artifactory/" balancer://${localNameAndPortArti}/artifactory/
    ProxyPassReverse "/artifactory/" balancer://${localNameAndPortArti}/artifactory/
    ProxyPass "/" balancer://${upstreamName}/ nocanon
    ProxyPassReverse "/" balancer://${upstreamName}/
</#if>
</VirtualHost>
