#!/bin/bash -ex
# Run from the top-level source directory 


# Set dstamp to the equivalent of what Ant uses for its DSTAMP variable.
dstamp=`date '+%Y%m%d'`
binary_tgz=${dstamp}-binary.tgz
echo "Setting current download to:" ${binary_tgz}


~/bin/ant clean binary-dist

scp -i ~/.ssh/celeste     dist/${binary_tgz}           asdf@celeste.sunlabs.com:/var/apache2/htdocs/install/
scp -C -i ~/.ssh/celeste     administration/webinstall    asdf@celeste.sunlabs.com:/var/apache2/htdocs/install/

ssh -i ~/.ssh/celeste asdf@celeste.sunlabs.com rm -rf /var/apache2/htdocs/javadoc
scp -C -i ~/.ssh/celeste -r web/javadoc                   asdf@celeste.sunlabs.com:/var/apache2/htdocs/

ssh -i ~/.ssh/celeste asdf@celeste.sunlabs.com rm -f /var/apache2/htdocs/install/latest
ssh -i ~/.ssh/celeste asdf@celeste.sunlabs.com ln -s /var/apache2/htdocs/install/${binary_tgz} /var/apache2/htdocs/install/latest
