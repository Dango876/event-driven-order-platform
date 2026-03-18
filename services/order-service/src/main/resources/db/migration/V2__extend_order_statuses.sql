alter table orders drop constraint if exists orders_status_check;

alter table orders
    add constraint orders_status_check
        check (status in ('NEW', 'RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'));
