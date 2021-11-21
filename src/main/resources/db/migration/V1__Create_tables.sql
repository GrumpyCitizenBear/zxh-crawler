create table NEWS(
id bigint primary key auto_increment,
title varchar(10000000000),
content varchar(10000000000),
url varchar(3000),
created_at timestamp,
modified_at timestamp
);

create table LINKS_TO_BE_PROCESSED(link varchar(3000));

create table LINKS_ALREADY_PROCESSED(link varchar(3000));