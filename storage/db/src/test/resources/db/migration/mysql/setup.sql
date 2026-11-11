CREATE DATABASE ${schema} CHARACTER SET utf8 COLLATE utf8_bin;
GRANT ALL on ${schema}.* TO 'artifactory'@'%';