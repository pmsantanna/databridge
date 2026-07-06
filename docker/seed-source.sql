-- Seed do banco de ORIGEM (sistema legado simulado)
-- Rodado automaticamente pelo Docker na inicialização

CREATE TABLE IF NOT EXISTS employees (
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    department VARCHAR(100),
    hired_at   DATE,
    active     BOOLEAN DEFAULT TRUE
);

INSERT INTO employees (id, name, department, hired_at, active) VALUES
  (1,  'Alice Mendes',     'Engineering',  '2019-03-15', TRUE),
  (2,  'Bruno Carvalho',   'Engineering',  '2020-07-01', TRUE),
  (3,  'Carla Souza',      'HR',           '2018-11-20', TRUE),
  (4,  'Diego Lima',       'Finance',      '2021-01-10', TRUE),
  (5,  'Elena Castro',     'Engineering',  '2017-06-05', TRUE),
  (6,  'Fábio Rocha',      'Legal',        '2022-09-14', TRUE),
  (7,  'Gabriela Nunes',   'HR',           '2020-04-22', TRUE),
  (8,  'Henrique Dias',    'Finance',      '2019-12-01', FALSE),
  (9,  'Isabela Ferreira', 'Engineering',  '2023-02-28', TRUE),
  (10, 'João Martins',     'Legal',        '2016-08-17', TRUE),
  (11, 'Karen Oliveira',   'HR',           '2021-05-30', TRUE),
  (12, 'Lucas Pereira',    'Engineering',  '2022-11-09', TRUE),
  (13, 'Mariana Costa',    'Finance',      '2018-03-25', TRUE),
  (14, 'Nicolas Alves',    'Engineering',  '2020-10-15', FALSE),
  (15, 'Olivia Santos',    'HR',           '2023-06-01', TRUE),
  (16, 'Paulo Ribeiro',    'Finance',      '2019-07-20', TRUE),
  (17, 'Quintino Moura',   'Legal',        '2017-02-14', TRUE),
  (18, 'Rafaela Lopes',    'Engineering',  '2022-04-05', TRUE),
  (19, 'Samuel Freitas',   'HR',           '2020-08-12', TRUE),
  (20, 'Tatiana Barbosa',  'Finance',      '2021-12-03', TRUE),
  (21, 'Ubirajara Gomes',  'Engineering',  '2018-09-18', FALSE),
  (22, 'Vera Nascimento',  'Legal',        '2023-01-15', TRUE),
  (23, 'Wagner Cardoso',   'Finance',      '2019-04-27', TRUE),
  (24, 'Xênia Tavares',    'HR',           '2022-07-08', TRUE),
  (25, 'Yago Correia',     'Engineering',  '2021-03-22', TRUE);

CREATE TABLE IF NOT EXISTS departments (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    cost_center VARCHAR(50),
    active      BOOLEAN DEFAULT TRUE
);

INSERT INTO departments (id, name, cost_center, active) VALUES
  (1, 'Engineering', 'CC-100', TRUE),
  (2, 'HR',          'CC-200', TRUE),
  (3, 'Finance',     'CC-300', TRUE),
  (4, 'Legal',       'CC-400', TRUE);

CREATE TABLE IF NOT EXISTS payroll_entries (
    id          BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    period      VARCHAR(7) NOT NULL,
    gross_pay   NUMERIC(12,2),
    net_pay     NUMERIC(12,2),
    paid_at     DATE
);

INSERT INTO payroll_entries (id, employee_id, period, gross_pay, net_pay, paid_at) VALUES
  (1,  1,  '2026-05', 9500.00, 7810.50, '2026-05-05'),
  (2,  2,  '2026-05', 8700.00, 7215.30, '2026-05-05'),
  (3,  3,  '2026-05', 6200.00, 5254.00, '2026-05-05'),
  (4,  4,  '2026-05', 7100.00, 5947.10, '2026-05-05'),
  (5,  5,  '2026-05', 9900.00, 8091.30, '2026-05-05'),
  (6,  1,  '2026-06', 9500.00, 7810.50, '2026-06-05'),
  (7,  2,  '2026-06', 8700.00, 7215.30, '2026-06-05'),
  (8,  3,  '2026-06', 6200.00, 5254.00, '2026-06-05'),
  (9,  4,  '2026-06', 7100.00, 5947.10, '2026-06-05'),
  (10, 5,  '2026-06', 9900.00, 8091.30, '2026-06-05');

CREATE TABLE IF NOT EXISTS positions (
    id            BIGINT PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    level         VARCHAR(50),
    department_id BIGINT
);

INSERT INTO positions (id, title, level, department_id) VALUES
  (1, 'Software Engineer',    'Pleno',  1),
  (2, 'Tech Lead',            'Senior', 1),
  (3, 'People Partner',       'Pleno',  2),
  (4, 'Financial Analyst',    'Junior', 3),
  (5, 'Legal Counsel',        'Senior', 4),
  (6, 'Engineering Manager',  'Senior', 1);

CREATE TABLE IF NOT EXISTS benefits (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    category     VARCHAR(50),
    monthly_cost NUMERIC(10,2)
);

INSERT INTO benefits (id, name, category, monthly_cost) VALUES
  (1, 'Plano de Saúde',       'Saúde',      450.00),
  (2, 'Vale Refeição',        'Alimentação', 800.00),
  (3, 'Plano Odontológico',   'Saúde',      60.00),
  (4, 'Gympass',              'Bem-estar',  120.00),
  (5, 'Seguro de Vida',       'Saúde',      35.00);

CREATE TABLE IF NOT EXISTS vacation_requests (
    id          BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    status      VARCHAR(20) DEFAULT 'PENDING'
);

INSERT INTO vacation_requests (id, employee_id, start_date, end_date, status) VALUES
  (1, 1,  '2026-01-05', '2026-01-20', 'APPROVED'),
  (2, 3,  '2026-02-10', '2026-02-24', 'APPROVED'),
  (3, 5,  '2026-03-01', '2026-03-15', 'APPROVED'),
  (4, 9,  '2026-04-12', '2026-04-26', 'PENDING'),
  (5, 12, '2026-05-20', '2026-06-03', 'PENDING'),
  (6, 18, '2026-07-01', '2026-07-15', 'REJECTED');
