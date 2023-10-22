# Note for Future Developers:

TODOs: 
- Refactor`legacy` usages to use NetworkManager in `api24` for checking connectivity
  - While doing that, avoid the practice of checking connectivity in Activities. Ideally, interactions with the backend should go through `Sync`, and connectivity should be checked in `Sync` package. 

