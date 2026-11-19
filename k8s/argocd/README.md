# ArgoCD Application 정의

## 파일

| 파일 | Application | 보는 경로 | 배포 네임스페이스 |
|---|---|---|---|
| `root.yaml` | `root` | `k8s/argocd` (이 폴더) | `argocd` |
| `application-dev.yaml` | `mozip-dev` | `k8s/overlays/dev` | `mozip-dev` |
| `application-prod.yaml` | `mozip` | `k8s/overlays/prod` | `mozip` |

`root`가 이 폴더를 동기화하므로 `application-*.yaml`을 고쳐 develop에 머지하면 클러스터의 Application 객체도 따라 바뀜. `root` 자신도 이 폴더에 있어 함께 관리됨.

## 처음 한 번만 수동 적용

ArgoCD는 자기 Application 정의를 Git에서 읽지 않음. 그래서 `root`만 사람이 적용하고, 나머지는 `root`가 만듦.

```bash
# control plane
cd ~/Mozip-BE && git pull
kubectl apply -f k8s/argocd/root.yaml
kubectl get applications.argoproj.io -n argocd
```

`root`, `mozip-dev`, `mozip` 셋이 `Synced`면 정상. 이미 수동으로 만들어 둔 `mozip-dev`, `mozip`은 `root`가 그대로 이어받음.

## 확인

```bash
kubectl get applications.argoproj.io -n argocd
kubectl get application mozip -n argocd -o jsonpath='{.status.conditions}'; echo
```

`SYNC`가 `Unknown`이고 conditions에 `app path does not exist`가 있으면 Application이 보는 `path`가 리포에 없는 것. 이 폴더의 파일을 고쳐 머지하면 `root`가 반영함. `root`를 적용하기 전이라면 해당 파일을 직접 `kubectl apply`.

## 지울 때

Application 파일을 지우고 머지해도 `root`는 `prune: false`라 클러스터의 Application을 지우지 않음. 실제로 내리려면 직접 삭제.

```bash
kubectl delete application <이름> -n argocd
```

`application-*.yaml`에는 `resources-finalizer`가 있어 Application을 지우면 그 앱이 배포한 리소스(디플로이먼트 등)도 함께 지워짐. prod는 특히 주의.
