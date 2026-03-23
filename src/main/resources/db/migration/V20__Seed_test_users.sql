-- Update admin to have a valid email format and ensuring the admin role is configured
UPDATE users SET email = 'admin@linkbit.com', role = 'ADMIN' WHERE email = 'admin';
