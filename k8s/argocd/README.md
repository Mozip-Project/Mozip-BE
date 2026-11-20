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

## 롤백은 어떻게 하나?

- 기본은 Git으로 되돌림. 문제가 된 이미지 태그 커밋(`chore: update dev image tag ...`)을 revert해서 develop에 머지하면 자동 동기화로 이전 이미지가 올라감
- ArgoCD 화면의 Rollback은 그 앱의 자동 동기화를 꺼야 쓸 수 있음
  - 그런데 `root`가 `selfHeal: true`로 `application-*.yaml`을 계속 맞추고 있어서 앱의 자동 동기화만 끄면 `root`가 곧바로 다시 켬
  - 그래서 `root` 자동 동기화를 먼저 끄고 진행함

```bash
# root 자동 동기화 끄기 (root는 자기 자신도 관리하지만 동기화가 꺼져 있으면 되돌리지 않음)
kubectl patch application root -n argocd --type merge -p '{"spec":{"syncPolicy":null}}'
# 그다음 대상 앱의 자동 동기화를 끄고 화면에서 Rollback
kubectl patch application mozip -n argocd --type merge -p '{"spec":{"syncPolicy":{"automated":null}}}'
```

- 끝나면 원인 커밋을 revert해 머지하고 `kubectl apply -f k8s/argocd/root.yaml`로 `root`를 되살림. `root`가 다시 돌면 앱의 자동 동기화도 파일대로 켜짐

## 지울 때

Application 파일을 지우고 머지해도 `root`는 `prune: false`이고 `application-*.yaml`에 `Delete=false` 어노테이션이 있어 클러스터의 Application을 지우지 않음. 실제로 내리려면 직접 삭제.

```bash
kubectl delete application <이름> -n argocd
```

`application-*.yaml`에는 `resources-finalizer`가 있어 Application을 지우면 그 앱이 배포한 리소스(디플로이먼트 등)도 함께 지워짐. prod는 특히 주의.

`root`를 지울 때도 조심해야 함.

- `argocd app delete root`는 기본이 cascade라 `root`가 만든 리소스, 곧 `mozip-dev`와 `mozip` Application까지 지우려 함
- 그러면 위 finalizer 때문에 dev와 prod 배포가 통째로 내려감. `Delete=false` 어노테이션이 이걸 막지만 그래도 아래처럼 지움

```bash
argocd app delete root --cascade=false
# 또는 (root에는 finalizer가 없어서 kubectl 삭제는 하위 Application을 건드리지 않음)
kubectl delete application root -n argocd
```
