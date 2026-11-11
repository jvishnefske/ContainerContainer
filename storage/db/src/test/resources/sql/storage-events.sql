UPDATE unique_ids SET current_id = 10000 WHERE index_type='general';

INSERT INTO storage_events VALUES
(100, 1677673006, 3, 'dcab88fc2a043c2479a6de676a2f8179e9ea2167','bbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb', 3, null),
(101, 1677673006, 3, 'acab88fc2a043c2479a6de676a2f8179e9ea2167','dddd23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60dddd', 78, '{"someKey":1,"retryCount":1}'),
(102, 1677673006, 3, 'bbbb88fc2a043c2479a6de676a2f8179e9eabbbb','yyyy23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 33, '{"retryCount":42}'),
(103, 1677673006, 3, 'dddd88fc2a043c2479a6de676a2f8179e9eadddd','dcabf055bc6d5477c35f82da16323efb884fc21a87fbf7ebda9d5848eee3e280', 333, null),
(104, 1677673006, 3, 'dddd88fc2a043c2479a6de676a2f7179e9eaddac','acdc23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60febd', 500, null),
(105, 1677673006, 3, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac','acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 666, null);