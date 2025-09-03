package cluster

import (
	"testing"

	"github.com/flamingo/openframe/internal/cluster/utils"
	"github.com/flamingo/openframe/tests/testutil"
)

func init() {
	testutil.InitializeTestMode()
}

func TestDeleteCommand(t *testing.T) {
	setupFunc := func() {
		utils.SetTestExecutor(testutil.NewTestMockExecutor())
	}
	teardownFunc := func() {
		utils.ResetGlobalFlags()
	}

	testutil.TestClusterCommand(t, "delete", getDeleteCmd, setupFunc, teardownFunc)
}