# chicken-api
🐔🌐

### TODO
- [x] Add more breeds to the breeds endpoint
- [x] Add more details to the breeds endpoint - number of eggs?
- [x] Add chicken endpoint so you can upload your own chicken
- [ ] Add google form to submit chicken
- [ ] Add a way to vote on chickens
### Rate limit configuration
Bucket4j limits are defined in `application.yml`. Adjust the `capacity` and `time` fields
under `bucket4j.filters` to tweak global or per-IP limits.
