package cluster

import (
	"testing"

	"github.com/flamingo/openframe/internal/cluster/utils"
	"github.com/flamingo/openframe/tests/testutil"
)

func init() {
	testutil.InitializeTestMode()
}

func TestListCommand(t *testing.T) {
	setupFunc := func() {
		utils.SetTestExecutor(testutil.NewTestMockExecutor())
	}
	teardownFunc := func() {
		utils.ResetGlobalFlags()
	}

	testutil.TestClusterCommand(t, "list", getListCmd, setupFunc, teardownFunc)
}