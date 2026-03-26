alter table orders drop constraint if exists orders_status_check;

alter table orders
    add constraint orders_status_check
        check (status in (
                          'NEW',
                          'RESERVATION_PENDING',
                          'RESERVED',
                          'RESERVATION_FAILED',
                          'PAID',
                          'SHIPPED',
                          'COMPLETED',
                          'CANCELLED'
            ));
